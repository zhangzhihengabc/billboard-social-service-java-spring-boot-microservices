package com.billboard.social.group.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.InputValidator;
import com.billboard.social.common.service.EmailService;
import com.billboard.social.group.dto.request.GroupRequests.*;
import com.billboard.social.group.dto.response.GroupResponses.*;
import com.billboard.social.group.entity.Group;
import com.billboard.social.group.entity.GroupInvitation;
import com.billboard.social.group.entity.GroupMember;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.repository.GroupInvitationRepository;
import com.billboard.social.group.repository.GroupMemberRepository;
import com.billboard.social.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupInvitationService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupInvitationRepository invitationRepository;
    private final UserServiceClient userServiceClient;
    private final EmailService emailService;

    @Value("${app.group.invitation.default-expiration-days:7}")
    private int defaultExpirationDays;

    @Value("${app.group.invitation.max-pending-per-user:100}")
    private int maxPendingPerUser;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ==================== SEND INVITATION ====================

    @Transactional
    public InvitationResponse inviteMember(UUID inviterId, UUID groupId, InviteMemberRequest request) {
        // Validate request
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        if (request.getUserId() == null && request.getEmail() == null) {
            throw new ValidationException("Either userId or email is required");
        }
        if (request.getMessage() != null) {
            InputValidator.validateText(request.getMessage(), "Message", 500);
        }
        if (request.getEmail() != null) {
            InputValidator.validateText(request.getEmail(), "Email", 255);
        }

        // Check inviter has permission (must be admin/moderator or group allows member invites)
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        GroupMember inviterMember = memberRepository.findByGroupIdAndUserId(groupId, inviterId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this group"));

        if (!inviterMember.isModerator() && !group.getAllowMemberInvites()) {
            throw new ForbiddenException("Only moderators can invite members to this group");
        }

        UUID inviteeId = request.getUserId();
        String inviteeEmail = request.getEmail();
        UserSummary invitee = null;

        // Case 1: Invite by User ID - fetch user's email
        if (inviteeId != null) {
            invitee = fetchUserSummary(inviteeId);
            inviteeEmail = invitee.getEmail();  // Get email from user service

            if (inviteeEmail == null || inviteeEmail.isBlank()) {
                log.warn("User {} has no email, invitation email will not be sent", inviteeId);
            }

            memberRepository.findByGroupIdAndUserId(groupId, inviteeId).ifPresent(existing -> {
                if (existing.getStatus() == MemberStatus.BANNED) {
                    throw new ValidationException("This user is banned from the group");
                }
                if (existing.getStatus() == MemberStatus.APPROVED) {
                    throw new ValidationException("User is already a member of this group");
                }
                if (existing.getStatus() == MemberStatus.PENDING) {
                    throw new ValidationException("User already has a pending join request");
                }
            });

            // Delete existing invitation if any (hard delete for unique constraint)
            invitationRepository.findByGroupIdAndInviteeId(groupId, inviteeId)
                    .ifPresent(existing -> {
                        if (existing.isPending()) {
                            throw new ValidationException("A pending invitation already exists for this user");
                        }
                        // Delete old accepted/declined/expired invitation to allow re-invite
                        invitationRepository.delete(existing);
                        log.info("Deleted old invitation {} to allow re-invite", existing.getId());
                    });
        }

        // Calculate expiration
        int expirationDays = request.getExpirationDays() != null ? request.getExpirationDays() : defaultExpirationDays;
        if (expirationDays < 1 || expirationDays > 30) {
            throw new ValidationException("Expiration days must be between 1 and 30");
        }

        GroupInvitation invitation = GroupInvitation.builder()
                .group(group)
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .inviteeEmail(inviteeEmail)
                .message(request.getMessage())
                .status("PENDING")
                .inviteCode(generateInviteCode())
                .expiresAt(LocalDateTime.now().plusDays(expirationDays))
                .build();

        invitation = invitationRepository.save(invitation);

        // Send invitation email (for both cases)
        if (inviteeEmail != null && !inviteeEmail.isBlank()) {
            UserSummary inviter = fetchUserSummary(inviterId);
            sendInvitationEmail(inviteeEmail, inviter.getUsername(), group.getName(),
                    invitation.getInviteCode(), request.getMessage());
        }

        log.info("User {} invited {} to group {}", inviterId,
                inviteeId != null ? inviteeId : inviteeEmail, groupId);

        return mapToInvitationResponse(invitation);
    }

    /**
     * Send invitation email asynchronously
     */
    private void sendInvitationEmail(String toEmail, String inviterName, String groupName,
                                     String inviteCode, String message) {
        try {
            emailService.sendGroupInvitationEmail(toEmail, inviterName, groupName, inviteCode, message);
        } catch (Exception e) {
            log.error("Failed to send invitation email to {}: {}", toEmail, e.getMessage());
            // Don't fail the invitation if email fails
        }
    }

    // ==================== CREATE INVITE LINK ====================

    @Transactional
    public InvitationResponse createInviteLink(UUID inviterId, UUID groupId, CreateInviteLinkRequest request) {
        // Validate request
        if (request != null && request.getMessage() != null) {
            InputValidator.validateText(request.getMessage(), "Message", 500);
        }

        // Check inviter has permission
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        GroupMember inviterMember = memberRepository.findByGroupIdAndUserId(groupId, inviterId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this group"));

        if (!inviterMember.isModerator() && !group.getAllowMemberInvites()) {
            throw new ForbiddenException("Only moderators can create invite links for this group");
        }

        // Calculate expiration
        int expirationDays = (request != null && request.getExpirationDays() != null)
                ? request.getExpirationDays() : defaultExpirationDays;
        if (expirationDays < 1 || expirationDays > 30) {
            throw new ValidationException("Expiration days must be between 1 and 30");
        }

        GroupInvitation invitation = GroupInvitation.builder()
                .group(group)
                .inviterId(inviterId)
                .inviteeId(null)  // Link-based invite, no specific invitee
                .message(request != null ? request.getMessage() : null)
                .status("PENDING")
                .inviteCode(generateInviteCode())
                .expiresAt(LocalDateTime.now().plusDays(expirationDays))
                .build();

        invitation = invitationRepository.save(invitation);

        log.info("User {} created invite link for group {}", inviterId, groupId);

        return mapToInvitationResponse(invitation);
    }

    // ==================== ACCEPT / DECLINE ====================

    @Transactional
    public GroupMemberResponse acceptInvitation(UUID userId, UUID invitationId) {
        GroupInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "id", invitationId));

        // Validate invitation belongs to user or is a public link
        if (invitation.getInviteeId() != null && !invitation.getInviteeId().equals(userId)) {
            throw new ForbiddenException("This invitation is not for you");
        }

        if (!invitation.isPending()) {
            throw new ValidationException("Invitation is no longer valid");
        }

        if (invitation.isExpired()) {
            throw new ValidationException("Invitation has expired");
        }

        Group group = invitation.getGroup();

        // Check if already a member
        memberRepository.findByGroupIdAndUserId(group.getId(), userId).ifPresent(existing -> {
            if (existing.getStatus() == MemberStatus.BANNED) {
                throw new ValidationException("You are banned from this group");
            }
            if (existing.getStatus() == MemberStatus.APPROVED) {
                throw new ValidationException("You are already a member of this group");
            }
        });

        // Create membership
        GroupMember member = GroupMember.builder()
                .group(group)
                .userId(userId)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.APPROVED)
                .build();
        member.approve(invitation.getInviterId());
        member = memberRepository.save(member);

        // Update group member count
        group.incrementMemberCount();
        groupRepository.save(group);

        invitationRepository.delete(invitation);

        log.info("User {} accepted invitation {} and joined group {}", userId, invitationId, group.getId());

        return mapToMemberResponse(member);
    }

    @Transactional
    public void declineInvitation(UUID userId, UUID invitationId) {
        GroupInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "id", invitationId));

        // Validate invitation belongs to user
        if (invitation.getInviteeId() != null && !invitation.getInviteeId().equals(userId)) {
            throw new ForbiddenException("This invitation is not for you");
        }

        if (!invitation.isPending()) {
            throw new ValidationException("Invitation is no longer valid");
        }

        // Hard delete to allow future re-invites (unique constraint)
        invitationRepository.delete(invitation);

        log.info("User {} declined and deleted invitation {}", userId, invitationId);
    }

    // ==================== ACCEPT BY CODE ====================

    @Transactional
    public GroupMemberResponse acceptByCode(UUID userId, String inviteCode) {
        // Validate input
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new ValidationException("Invite code is required");
        }

        GroupInvitation invitation = invitationRepository.findByInviteCode(inviteCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite code"));

        if (!invitation.isPending()) {
            throw new ValidationException("Invitation is no longer valid");
        }

        if (invitation.isExpired()) {
            throw new ValidationException("Invitation has expired");
        }

        Group group = invitation.getGroup();

        // Check if already a member
        memberRepository.findByGroupIdAndUserId(group.getId(), userId).ifPresent(existing -> {
            if (existing.getStatus() == MemberStatus.BANNED) {
                throw new ValidationException("You are banned from this group");
            }
            if (existing.getStatus() == MemberStatus.APPROVED) {
                throw new ValidationException("You are already a member of this group");
            }
        });

        // Create membership
        GroupMember member = GroupMember.builder()
                .group(group)
                .userId(userId)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.APPROVED)
                .build();
        member.approve(invitation.getInviterId());
        member = memberRepository.save(member);

        // Update group member count
        group.incrementMemberCount();
        groupRepository.save(group);

        // For user-specific invites, delete after accepting
        // For link-based invites (inviteeId is null), keep for others to use
        if (invitation.getInviteeId() != null) {
            invitationRepository.delete(invitation);
        }

        log.info("User {} joined group {} via invite code", userId, group.getId());

        return mapToMemberResponse(member);
    }

    // ==================== CANCEL INVITATION ====================

    @Transactional
    public void cancelInvitation(UUID adminId, UUID groupId, UUID invitationId) {
        checkModeratorAccess(adminId, groupId);

        GroupInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "id", invitationId));

        if (!invitation.getGroup().getId().equals(groupId)) {
            throw new ValidationException("Invitation does not belong to this group");
        }

        // Hard delete (Section 10.1 - unique constraint)
        invitationRepository.delete(invitation);

        log.info("Admin {} cancelled invitation {} in group {}", adminId, invitationId, groupId);
    }

    // ==================== GET INVITATIONS ====================

    @Transactional(readOnly = true)
    public InvitationResponse getInvitation(UUID invitationId) {
        GroupInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "id", invitationId));
        return mapToInvitationResponse(invitation);
    }

    @Transactional(readOnly = true)
    public InvitationResponse getInvitationByCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new ValidationException("Invite code is required");
        }

        GroupInvitation invitation = invitationRepository.findByInviteCode(inviteCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite code"));

        return mapToInvitationResponse(invitation);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> getMyPendingInvitations(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<GroupInvitation> invitations = invitationRepository.findPendingInvitationsForUser(
                userId, LocalDateTime.now(), pageRequest);
        return PageResponse.from(invitations, this::mapToInvitationResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> getGroupInvitations(UUID adminId, UUID groupId, int page, int size) {
        checkModeratorAccess(adminId, groupId);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<GroupInvitation> invitations = invitationRepository.findPendingInvitationsByGroup(
                groupId, LocalDateTime.now(), pageRequest);
        return PageResponse.from(invitations, this::mapToInvitationResponse);
    }

    @Transactional(readOnly = true)
    public long countPendingInvitations(UUID userId) {
        return invitationRepository.countPendingInvitationsForUser(userId, LocalDateTime.now());
    }

    // ==================== PRIVATE HELPERS ====================

    private void checkModeratorAccess(UUID userId, UUID groupId) {
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this group"));

        if (!member.isModerator()) {
            throw new ForbiddenException("Moderator access required");
        }
    }

    private String generateInviteCode() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private InvitationResponse mapToInvitationResponse(GroupInvitation invitation) {
        Group group = invitation.getGroup();
        UserSummary inviter = fetchUserSummary(invitation.getInviterId());

        return InvitationResponse.builder()
                .id(invitation.getId())
                .groupId(group.getId())
                .groupName(group.getName())
                .groupIconUrl(group.getIconUrl())
                .inviterId(invitation.getInviterId())
                .inviterName(inviter.getUsername())
                .inviteeId(invitation.getInviteeId())
                .inviteeEmail(invitation.getInviteeEmail())
                .message(invitation.getMessage())
                .status(invitation.getStatus())
                .inviteCode(invitation.getInviteCode())
                .createdAt(invitation.getCreatedAt())
                .expiresAt(invitation.getExpiresAt())
                .acceptedAt(invitation.getAcceptedAt())
                .declinedAt(invitation.getDeclinedAt())
                .inviter(inviter)
                .build();
    }

    private GroupMemberResponse mapToMemberResponse(GroupMember member) {
        UserSummary userSummary = fetchUserSummary(member.getUserId());

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

    // Section 7.1 - Feign error handling with fallback
    private UserSummary fetchUserSummary(UUID userId) {
        try {
            return userServiceClient.getUserSummary(userId);
        } catch (Exception e) {
            log.warn("Failed to fetch user summary for {}: {}", userId, e.getMessage());
            return UserSummary.builder()
                    .id(userId)
                    .username("Unknown")
                    .build();
        }
    }
}