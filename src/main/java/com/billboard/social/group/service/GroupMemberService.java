package com.billboard.social.group.service;
import com.billboard.social.common.dto.UserSummary;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.group.dto.request.GroupRequests.*;
import com.billboard.social.group.dto.response.GroupResponses.*;
import com.billboard.social.group.entity.Group;
import com.billboard.social.group.entity.GroupMember;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.event.GroupEventPublisher;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.group.repository.GroupInvitationRepository;
import com.billboard.social.group.repository.GroupMemberRepository;
import com.billboard.social.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupMemberService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupInvitationRepository invitationRepository;
    private final UserServiceClient userServiceClient;
    private final GroupEventPublisher eventPublisher;

    @Value("${app.group.max-members:100000}")
    private int maxMembers;

    @Value("${app.group.max-admins:100}")
    private int maxAdmins;

    @Transactional
    public GroupMemberResponse joinGroup(UUID userId, UUID groupId, JoinGroupRequest request) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        // Check if already a member
        if (memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ValidationException("Already a member or pending request exists");
        }

        // Check member limit
        if (group.getMemberCount() >= maxMembers) {
            throw new ValidationException("Group has reached maximum member capacity");
        }

        GroupMember member = GroupMember.builder()
            .group(group)
            .userId(userId)
            .role(MemberRole.MEMBER)
            .status(group.requiresApproval() ? MemberStatus.PENDING : MemberStatus.APPROVED)
            .build();

        if (!group.requiresApproval()) {
            member.approve(userId);
            group.incrementMemberCount();
            groupRepository.save(group);
        }

        member = memberRepository.save(member);

        if (member.isApproved()) {
            eventPublisher.publishMemberJoined(member);
        } else {
            eventPublisher.publishJoinRequested(member);
        }

        log.info("User {} joined group {} with status {}", userId, groupId, member.getStatus());
        return mapToMemberResponse(member);
    }

    @Transactional
    @CacheEvict(value = "groups", key = "#groupId")
    public GroupMemberResponse approveJoinRequest(UUID adminId, UUID groupId, UUID memberId) {
        checkModeratorAccess(adminId, groupId);

        GroupMember member = memberRepository.findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        if (!member.getGroup().getId().equals(groupId)) {
            throw new ValidationException("Member does not belong to this group");
        }

        if (!member.isPending()) {
            throw new ValidationException("Request is not pending");
        }

        member.approve(adminId);
        member = memberRepository.save(member);

        Group group = member.getGroup();
        group.incrementMemberCount();
        groupRepository.save(group);

        eventPublisher.publishMemberApproved(member);

        log.info("Join request {} approved by {}", memberId, adminId);
        return mapToMemberResponse(member);
    }

    @Transactional
    public void rejectJoinRequest(UUID adminId, UUID groupId, UUID memberId) {
        checkModeratorAccess(adminId, groupId);

        GroupMember member = memberRepository.findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        if (!member.getGroup().getId().equals(groupId)) {
            throw new ValidationException("Member does not belong to this group");
        }

        member.reject();
        memberRepository.save(member);

        log.info("Join request {} rejected by {}", memberId, adminId);
    }

    @Transactional
    @CacheEvict(value = "groups", key = "#groupId")
    public void leaveGroup(UUID userId, UUID groupId) {
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));

        if (member.getRole() == MemberRole.OWNER) {
            throw new ValidationException("Owner cannot leave the group. Transfer ownership first.");
        }

        member.leave();
        memberRepository.save(member);

        Group group = member.getGroup();
        group.decrementMemberCount();
        groupRepository.save(group);

        eventPublisher.publishMemberLeft(member);

        log.info("User {} left group {}", userId, groupId);
    }

    @Transactional
    @CacheEvict(value = "groups", key = "#groupId")
    public void removeMember(UUID adminId, UUID groupId, UUID userId) {
        checkModeratorAccess(adminId, groupId);

        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (member.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("Cannot remove the owner");
        }

        // Check if trying to remove higher role
        GroupMember admin = memberRepository.findByGroupIdAndUserId(groupId, adminId).get();
        if (member.getRole().ordinal() >= admin.getRole().ordinal() && admin.getRole() != MemberRole.OWNER) {
            throw new ForbiddenException("Cannot remove member with equal or higher role");
        }

        member.softDelete();
        memberRepository.save(member);

        Group group = member.getGroup();
        group.decrementMemberCount();
        groupRepository.save(group);

        eventPublisher.publishMemberRemoved(member, adminId);

        log.info("User {} removed from group {} by {}", userId, groupId, adminId);
    }

    @Transactional
    public GroupMemberResponse updateMemberRole(UUID adminId, UUID groupId, UUID userId, UpdateMemberRoleRequest request) {
        checkAdminAccess(adminId, groupId);

        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        GroupMember admin = memberRepository.findByGroupIdAndUserId(groupId, adminId).get();

        // Only owner can promote to admin
        if (request.getRole() == MemberRole.ADMIN && admin.getRole() != MemberRole.OWNER) {
            throw new ForbiddenException("Only owner can promote to admin");
        }

        // Cannot change owner role
        if (member.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("Cannot change owner's role");
        }

        // Check admin limit
        if (request.getRole() == MemberRole.ADMIN) {
            List<GroupMember> admins = memberRepository.findAdmins(groupId);
            if (admins.size() >= maxAdmins) {
                throw new ValidationException("Maximum admin limit reached");
            }
        }

        member.setRole(request.getRole());
        member = memberRepository.save(member);

        log.info("Member {} role updated to {} by {}", userId, request.getRole(), adminId);
        return mapToMemberResponse(member);
    }

    @Transactional
    public GroupMemberResponse muteMember(UUID adminId, UUID groupId, UUID userId, MuteMemberRequest request) {
        checkModeratorAccess(adminId, groupId);

        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (member.isAdmin()) {
            throw new ForbiddenException("Cannot mute admins");
        }

        member.setMutedUntil(LocalDateTime.now().plusHours(request.getDurationHours()));
        member = memberRepository.save(member);

        log.info("Member {} muted in group {} until {}", userId, groupId, member.getMutedUntil());
        return mapToMemberResponse(member);
    }

    @Transactional
    @CacheEvict(value = "groups", key = "#groupId")
    public void banMember(UUID adminId, UUID groupId, UUID userId, BanMemberRequest request) {
        checkModeratorAccess(adminId, groupId);

        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (member.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("Cannot ban the owner");
        }

        GroupMember admin = memberRepository.findByGroupIdAndUserId(groupId, adminId).get();
        if (member.getRole().ordinal() >= admin.getRole().ordinal()) {
            throw new ForbiddenException("Cannot ban member with equal or higher role");
        }

        member.ban();
        memberRepository.save(member);

        Group group = member.getGroup();
        group.decrementMemberCount();
        groupRepository.save(group);

        eventPublisher.publishMemberBanned(member, adminId, request.getReason());

        log.info("Member {} banned from group {} by {} for: {}", userId, groupId, adminId, request.getReason());
    }

    @Transactional
    public void transferOwnership(UUID ownerId, UUID groupId, UUID newOwnerId) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        if (!group.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("Only the owner can transfer ownership");
        }

        GroupMember currentOwner = memberRepository.findByGroupIdAndUserId(groupId, ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Owner membership not found"));

        GroupMember newOwner = memberRepository.findByGroupIdAndUserId(groupId, newOwnerId)
            .orElseThrow(() -> new ResourceNotFoundException("New owner must be a member"));

        if (!newOwner.isApproved()) {
            throw new ValidationException("New owner must be an approved member");
        }

        currentOwner.setRole(MemberRole.ADMIN);
        newOwner.setRole(MemberRole.OWNER);
        
        group.setOwnerId(newOwnerId);

        memberRepository.save(currentOwner);
        memberRepository.save(newOwner);
        groupRepository.save(group);

        log.info("Ownership of group {} transferred from {} to {}", groupId, ownerId, newOwnerId);
    }

    @Transactional(readOnly = true)
    public Page<GroupMemberResponse> getMembers(UUID groupId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "joinedAt"));
        Page<GroupMember> members = memberRepository.findApprovedMembers(groupId, pageRequest);
        return members.map(this::mapToMemberResponse);
    }

    @Transactional(readOnly = true)
    public Page<GroupMemberResponse> getPendingMembers(UUID groupId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<GroupMember> members = memberRepository.findPendingMembers(groupId, pageRequest);
        return members.map(this::mapToMemberResponse);
    }

    @Transactional(readOnly = true)
    public List<UUID> getMemberIds(UUID groupId) {
        return memberRepository.findMemberUserIds(groupId);
    }

    private void checkAdminAccess(UUID userId, UUID groupId) {
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
            .orElseThrow(() -> new ForbiddenException("You are not a member of this group"));

        if (!member.isAdmin()) {
            throw new ForbiddenException("Admin access required");
        }
    }

    private void checkModeratorAccess(UUID userId, UUID groupId) {
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
            .orElseThrow(() -> new ForbiddenException("You are not a member of this group"));

        if (!member.isModerator()) {
            throw new ForbiddenException("Moderator access required");
        }
    }

    private GroupMemberResponse mapToMemberResponse(GroupMember member) {
        UserSummary userSummary = userServiceClient.getUserSummary(member.getUserId());

        return GroupMemberResponse.builder()
            .id(member.getId())
            .groupId(member.getGroup().getId())
            .userId(member.getUserId())
            .role(member.getRole())
            .status(member.getStatus())
            .joinedAt(member.getJoinedAt())
            .postCount(member.getPostCount())
            .contributionScore(member.getContributionScore())
            .notificationsEnabled(member.getNotificationsEnabled())
            .mutedUntil(member.getMutedUntil())
            .user(userSummary)
            .build();
    }
}
