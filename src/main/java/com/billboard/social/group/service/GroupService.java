package com.billboard.social.group.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.security.InputValidator;
import com.billboard.social.group.dto.request.GroupRequests.*;
import com.billboard.social.group.dto.response.GroupResponses.*;
import com.billboard.social.group.entity.Group;
import com.billboard.social.group.entity.GroupMember;
import com.billboard.social.group.entity.enums.GroupType;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.event.GroupEventPublisher;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
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
    public GroupResponse createGroup(Long userId, CreateGroupRequest request) {
        // Validate and sanitize name
        String validatedName = InputValidator.validateName(request.getName(), "Group name");

        long userGroupCount = groupRepository.countGroupsByMember(userId);
        if (userGroupCount >= maxUserGroups) {
            throw new ValidationException("Maximum group limit reached");
        }

        String slug = generateSlug(validatedName);

        // Validate and sanitize optional text fields
        String validatedDescription = InputValidator.validateText(request.getDescription(), "Description", 2000);
        String validatedLocation = InputValidator.validateText(request.getLocation(), "Location", 200);
        String validatedWebsite = InputValidator.validateText(request.getWebsite(), "Website", 500);
        String validatedRules = InputValidator.validateText(request.getRules(), "Rules", 5000);

        Group group = Group.builder()
                .name(validatedName)
                .slug(slug)
                .description(validatedDescription)
                .groupType(request.getGroupType() != null ? request.getGroupType() : GroupType.PUBLIC)
                .ownerId(userId)
                .categoryId(request.getCategoryId())
                .location(validatedLocation)
                .website(validatedWebsite)
                .rules(validatedRules)
                .allowMemberPosts(request.getAllowMemberPosts() != null ? request.getAllowMemberPosts() : true)
                .requirePostApproval(request.getRequirePostApproval() != null ? request.getRequirePostApproval() : false)
                .requireJoinApproval(request.getRequireJoinApproval() != null ? request.getRequireJoinApproval() : false)
                .allowMemberInvites(request.getAllowMemberInvites() != null ? request.getAllowMemberInvites() : true)
                .build();

        try {
            group = groupRepository.save(group);
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition detected for group creation with slug {}: {}", slug, e.getMessage());
            throw new ValidationException("A group with this name already exists");
        }

        GroupMember owner = GroupMember.builder()
                .group(group)
                .userId(userId)
                .role(MemberRole.OWNER)
                .status(MemberStatus.APPROVED)
                .build();
        owner.approve(userId);
        memberRepository.save(owner);

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
    public GroupResponse updateGroup(Long userId, UUID groupId, UpdateGroupRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ValidationException("Group not found with id: " + groupId));

        checkAdminAccess(userId, groupId);

        if (request.getName() != null) {
            String validatedName = InputValidator.validateName(request.getName(), "Group name");
            group.setName(validatedName);
            group.setSlug(generateSlug(validatedName));
        }
        if (request.getDescription() != null) {
            String validatedDescription = InputValidator.validateText(request.getDescription(), "Description", 2000);
            group.setDescription(validatedDescription);
        }
        if (request.getGroupType() != null) {
            group.setGroupType(request.getGroupType());
        }
        if (request.getCategoryId() != null) {
            group.setCategoryId(request.getCategoryId());
        }
        if (request.getLocation() != null) {
            String validatedLocation = InputValidator.validateText(request.getLocation(), "Location", 200);
            group.setLocation(validatedLocation);
        }
        if (request.getWebsite() != null) {
            String validatedWebsite = InputValidator.validateText(request.getWebsite(), "Website", 500);
            group.setWebsite(validatedWebsite);
        }
        if (request.getRules() != null) {
            String validatedRules = InputValidator.validateText(request.getRules(), "Rules", 5000);
            group.setRules(validatedRules);
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

        try {
            group = groupRepository.save(group);
        } catch (DataIntegrityViolationException e) {
            log.warn("Slug conflict during group update {}: {}", groupId, e.getMessage());
            throw new ValidationException("A group with this name already exists");
        }

        log.info("Group {} updated by user {}", groupId, userId);
        return mapToGroupResponse(group, userId);
    }

    @Transactional
    @CacheEvict(value = "groups", key = "#groupId")
    public void deleteGroup(Long userId, UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ValidationException("Group not found with id: " + groupId));

        if (!group.getOwnerId().equals(userId)) {
            throw new ForbiddenException("Only the owner can delete the group");
        }

        if (group.getCategoryId() != null) {
            categoryRepository.findById(group.getCategoryId())
                    .ifPresent(cat -> {
                        cat.setGroupCount(Math.max(0, cat.getGroupCount() - 1));
                        categoryRepository.save(cat);
                    });
        }

        memberRepository.deleteByGroupId(groupId);
        groupRepository.delete(group);

        eventPublisher.publishGroupDeleted(group);

        log.info("Group {} deleted by user {}", groupId, userId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "groups", key = "#groupId")
    public GroupResponse getGroup(UUID groupId, Long currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ValidationException("Group not found with id: " + groupId));

        if (group.getGroupType() == GroupType.PRIVATE || group.getGroupType() == GroupType.SECRET) {
            if (currentUserId == null || !isMember(groupId, currentUserId)) {
                throw new ForbiddenException("This group is private");
            }
        }

        return mapToGroupResponse(group, currentUserId);
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroupBySlug(String slug, Long currentUserId) {
        if (slug == null || slug.isBlank()) {
            throw new ValidationException("Slug is required");
        }

        // Sanitize slug - remove null bytes
        String sanitizedSlug = slug.replace("\u0000", "").trim();

        Group group = groupRepository.findBySlug(sanitizedSlug)
                .orElseThrow(() -> new ValidationException("Group not found with slug: " + sanitizedSlug));

        if (group.getGroupType() == GroupType.PRIVATE || group.getGroupType() == GroupType.SECRET) {
            if (currentUserId == null || !isMember(group.getId(), currentUserId)) {
                throw new ForbiddenException("This group is private");
            }
        }

        return mapToGroupResponse(group, currentUserId);
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupSummaryResponse> searchGroups(String query, int page, int size) {
        // Sanitize search query
        String sanitizedQuery = InputValidator.sanitizeSearchQuery(query);

        // Return empty results if query is empty after sanitization
        if (sanitizedQuery.isEmpty()) {
            return PageResponse.empty(page, size, "memberCount", "DESC");
        }

        // In-memory search for safety (avoids SQL injection and special char issues)
        List<Group> allPublicGroups = groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC);
        String lowerQuery = sanitizedQuery.toLowerCase();

        List<Group> filtered = allPublicGroups.stream()
                .filter(g -> (g.getName() != null && g.getName().toLowerCase().contains(lowerQuery)) ||
                        (g.getDescription() != null && g.getDescription().toLowerCase().contains(lowerQuery)))
                .toList();

        // Manual pagination
        int start = page * size;
        int end = Math.min(start + size, filtered.size());

        List<Group> pageContent = start < filtered.size()
                ? filtered.subList(start, end)
                : List.of();

        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);

        return PageResponse.<GroupSummaryResponse>builder()
                .content(pageContent.stream().map(this::mapToGroupSummary).toList())
                .page(page)
                .size(size)
                .totalElements((long) filtered.size())
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1 || totalPages == 0)
                .empty(pageContent.isEmpty())
                .sort(PageResponse.SortInfo.builder()
                        .empty(false)
                        .sorted(true)
                        .unsorted(false)
                        .sortBy("memberCount")
                        .direction("DESC")
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupSummaryResponse> getPopularGroups(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Group> groups = groupRepository.findPopularGroups(pageRequest);
        return PageResponse.from(groups, this::mapToGroupSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupSummaryResponse> getFeaturedGroups(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Group> groups = groupRepository.findFeaturedGroups(pageRequest);
        return PageResponse.from(groups, this::mapToGroupSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupSummaryResponse> getGroupsByCategory(UUID categoryId, int page, int size) {
        if (categoryId == null) {
            throw new ValidationException("Category ID is required");
        }

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Group> groups = groupRepository.findByCategoryIdOrderByPopularity(categoryId, pageRequest);
        return PageResponse.from(groups, this::mapToGroupSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<MembershipResponse> getUserGroups(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "joinedAt"));
        Page<GroupMember> memberships = memberRepository.findMembershipsByUser(userId, pageRequest);
        return PageResponse.from(memberships, this::mapToMembershipResponse);
    }

    private boolean isMember(UUID groupId, Long userId) {
        return memberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.APPROVED);
    }

    private void checkAdminAccess(Long userId, UUID groupId) {
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

        // Handle empty slug (if input was all special characters)
        if (slug.isEmpty()) {
            slug = "group-" + System.currentTimeMillis();
        }

        String baseSlug = slug;
        int counter = 1;
        while (groupRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private GroupResponse mapToGroupResponse(Group group, Long currentUserId) {
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