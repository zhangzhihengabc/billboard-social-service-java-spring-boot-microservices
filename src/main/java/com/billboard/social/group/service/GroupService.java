package com.billboard.social.group.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.group.dto.request.GroupRequests.*;
import com.billboard.social.group.dto.response.GroupResponses.*;
import com.billboard.social.group.entity.Group;
import com.billboard.social.group.entity.GroupMember;
import com.billboard.social.group.entity.enums.GroupType;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.event.GroupEventPublisher;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.group.repository.GroupCategoryRepository;
import com.billboard.social.group.repository.GroupMemberRepository;
import com.billboard.social.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupCategoryRepository categoryRepository;
    private final UserServiceClient userServiceClient;
    private final GroupEventPublisher eventPublisher;

    @Value("${app.group.max-user-groups:500}")
    private int maxUserGroups;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    @Transactional
    public GroupResponse createGroup(UUID userId, CreateGroupRequest request) {
        // Check user group limit
        long userGroupCount = groupRepository.countGroupsByMember(userId);
        if (userGroupCount >= maxUserGroups) {
            throw new ValidationException("Maximum group limit reached");
        }

        String slug = generateSlug(request.getName());

        Group group = Group.builder()
            .name(request.getName())
            .slug(slug)
            .description(request.getDescription())
            .groupType(request.getGroupType())
            .ownerId(userId)
            .categoryId(request.getCategoryId())
            .location(request.getLocation())
            .website(request.getWebsite())
            .rules(request.getRules())
            .allowMemberPosts(request.getAllowMemberPosts())
            .requirePostApproval(request.getRequirePostApproval())
            .requireJoinApproval(request.getRequireJoinApproval())
            .allowMemberInvites(request.getAllowMemberInvites())
            .build();

        group = groupRepository.save(group);

        // Add creator as owner
        GroupMember owner = GroupMember.builder()
            .group(group)
            .userId(userId)
            .role(MemberRole.OWNER)
            .status(MemberStatus.APPROVED)
            .build();
        owner.approve(userId);
        memberRepository.save(owner);

        // Update category count
        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                .ifPresent(cat -> {
                    cat.setGroupCount(cat.getGroupCount() + 1);
                    categoryRepository.save(cat);
                });
        }

        eventPublisher.publishGroupCreated(group);

        log.info("Group {} created by user {}", group.getId(), userId);
        return mapToGroupResponse(group, userId);
    }

    @Transactional
    @CacheEvict(value = "groups", key = "#groupId")
    public GroupResponse updateGroup(UUID userId, UUID groupId, UpdateGroupRequest request) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        checkAdminAccess(userId, groupId);

        if (request.getName() != null) {
            group.setName(request.getName());
            group.setSlug(generateSlug(request.getName()));
        }
        if (request.getDescription() != null) {
            group.setDescription(request.getDescription());
        }
        if (request.getGroupType() != null) {
            group.setGroupType(request.getGroupType());
        }
        if (request.getCategoryId() != null) {
            group.setCategoryId(request.getCategoryId());
        }
        if (request.getLocation() != null) {
            group.setLocation(request.getLocation());
        }
        if (request.getWebsite() != null) {
            group.setWebsite(request.getWebsite());
        }
        if (request.getRules() != null) {
            group.setRules(request.getRules());
        }
        if (request.getAllowMemberPosts() != null) {
            group.setAllowMemberPosts(request.getAllowMemberPosts());
        }
        if (request.getRequirePostApproval() != null) {
            group.setRequirePostApproval(request.getRequirePostApproval());
        }
        if (request.getRequireJoinApproval() != null) {
            group.setRequireJoinApproval(request.getRequireJoinApproval());
        }
        if (request.getAllowMemberInvites() != null) {
            group.setAllowMemberInvites(request.getAllowMemberInvites());
        }

        group = groupRepository.save(group);

        log.info("Group {} updated by user {}", groupId, userId);
        return mapToGroupResponse(group, userId);
    }

    @Transactional
    @CacheEvict(value = "groups", key = "#groupId")
    public void deleteGroup(UUID userId, UUID groupId) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        if (!group.getOwnerId().equals(userId)) {
            throw new ForbiddenException("Only the owner can delete the group");
        }

        group.softDelete();
        groupRepository.save(group);

        eventPublisher.publishGroupDeleted(group);

        log.info("Group {} deleted by user {}", groupId, userId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "groups", key = "#groupId")
    public GroupResponse getGroup(UUID groupId, UUID currentUserId) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        // Check visibility for private/secret groups
        if (group.getGroupType() == GroupType.PRIVATE || group.getGroupType() == GroupType.SECRET) {
            if (currentUserId == null || !isMember(groupId, currentUserId)) {
                throw new ForbiddenException("This group is private");
            }
        }

        return mapToGroupResponse(group, currentUserId);
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroupBySlug(String slug, UUID currentUserId) {
        Group group = groupRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Slug Not Found"));

        if (group.getGroupType() == GroupType.PRIVATE || group.getGroupType() == GroupType.SECRET) {
            if (currentUserId == null || !isMember(group.getId(), currentUserId)) {
                throw new ForbiddenException("This group is private");
            }
        }

        return mapToGroupResponse(group, currentUserId);
    }

    @Transactional(readOnly = true)
    public Page<GroupSummaryResponse> searchGroups(String query, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "memberCount"));
        Page<Group> groups = groupRepository.searchGroups(query, pageRequest);
        return groups.map(this::mapToGroupSummary);
    }

    @Transactional(readOnly = true)
    public Page<GroupSummaryResponse> getPopularGroups(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Group> groups = groupRepository.findPopularGroups(pageRequest);
        return groups.map(this::mapToGroupSummary);
    }

    @Transactional(readOnly = true)
    public Page<GroupSummaryResponse> getFeaturedGroups(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Group> groups = groupRepository.findFeaturedGroups(pageRequest);
        return groups.map(this::mapToGroupSummary);
    }

    @Transactional(readOnly = true)
    public Page<GroupSummaryResponse> getGroupsByCategory(UUID categoryId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Group> groups = groupRepository.findByCategoryIdOrderByPopularity(categoryId, pageRequest);
        return groups.map(this::mapToGroupSummary);
    }

    @Transactional(readOnly = true)
    public Page<MembershipResponse> getUserGroups(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "joinedAt"));
        Page<GroupMember> memberships = memberRepository.findMembershipsByUser(userId, pageRequest);
        return memberships.map(this::mapToMembershipResponse);
    }

    private boolean isMember(UUID groupId, UUID userId) {
        return memberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.APPROVED);
    }

    private void checkAdminAccess(UUID userId, UUID groupId) {
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
            .orElseThrow(() -> new ForbiddenException("You are not a member of this group"));

        if (!member.isAdmin()) {
            throw new ForbiddenException("Admin access required");
        }
    }

    private String generateSlug(String input) {
        String noWhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH).replaceAll("-+", "-").replaceAll("^-|-$", "");
        
        // Ensure uniqueness
        String baseSlug = slug;
        int counter = 1;
        while (groupRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private GroupResponse mapToGroupResponse(Group group, UUID currentUserId) {
        GroupResponse.GroupResponseBuilder builder = GroupResponse.builder()
            .id(group.getId())
            .name(group.getName())
            .slug(group.getSlug())
            .description(group.getDescription())
            .groupType(group.getGroupType())
            .ownerId(group.getOwnerId())
            .categoryId(group.getCategoryId())
            .coverImageUrl(group.getCoverImageUrl())
            .iconUrl(group.getIconUrl())
            .location(group.getLocation())
            .website(group.getWebsite())
            .rules(group.getRules())
            .memberCount(group.getMemberCount())
            .postCount(group.getPostCount())
            .isVerified(group.getIsVerified())
            .isFeatured(group.getIsFeatured())
            .allowMemberPosts(group.getAllowMemberPosts())
            .requirePostApproval(group.getRequirePostApproval())
            .requireJoinApproval(group.getRequireJoinApproval())
            .allowMemberInvites(group.getAllowMemberInvites())
            .createdAt(group.getCreatedAt());

        if (group.getCategoryId() != null) {
            categoryRepository.findById(group.getCategoryId())
                .ifPresent(cat -> builder.categoryName(cat.getName()));
        }

        if (currentUserId != null) {
            memberRepository.findByGroupIdAndUserId(group.getId(), currentUserId)
                .ifPresent(member -> {
                    builder.isMember(member.isApproved());
                    builder.isAdmin(member.isAdmin());
                    builder.isPending(member.isPending());
                    builder.userRole(member.getRole());
                });
        }

        return builder.build();
    }

    private GroupSummaryResponse mapToGroupSummary(Group group) {
        return GroupSummaryResponse.builder()
            .id(group.getId())
            .name(group.getName())
            .slug(group.getSlug())
            .groupType(group.getGroupType())
            .iconUrl(group.getIconUrl())
            .memberCount(group.getMemberCount())
            .isVerified(group.getIsVerified())
            .build();
    }

    private MembershipResponse mapToMembershipResponse(GroupMember member) {
        Group group = member.getGroup();
        return MembershipResponse.builder()
            .groupId(group.getId())
            .groupName(group.getName())
            .groupSlug(group.getSlug())
            .groupIconUrl(group.getIconUrl())
            .groupType(group.getGroupType())
            .role(member.getRole())
            .status(member.getStatus())
            .joinedAt(member.getJoinedAt())
            .notificationsEnabled(member.getNotificationsEnabled())
            .build();
    }
}
