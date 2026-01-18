package com.billboard.content.forum.service;

import com.billboard.content.client.UserServiceClient;
import com.billboard.content.forum.dto.request.ForumRequests.*;
import com.billboard.content.forum.dto.response.ForumResponses.*;
import com.billboard.content.dto.UserSummary;
import com.billboard.content.forum.entity.Forum;
import com.billboard.content.forum.entity.Topic;
import com.billboard.content.forum.event.ForumEventPublisher;
import com.billboard.content.exception.ResourceNotFoundException;
import com.billboard.content.exception.ValidationException;
import com.billboard.content.forum.repository.ForumRepository;
import com.billboard.content.forum.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForumService {

    private final ForumRepository forumRepository;
    private final TopicRepository topicRepository;
    private final UserServiceClient userServiceClient;
    private final ForumEventPublisher eventPublisher;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    @Transactional
    public ForumResponse createForum(CreateForumRequest request) {
        String slug = generateSlug(request.getName());

        Forum forum = Forum.builder()
            .name(request.getName())
            .slug(slug)
            .description(request.getDescription())
            .forumType(request.getForumType())
            .groupId(request.getGroupId())
            .icon(request.getIcon())
            .color(request.getColor())
            .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
            .requiresApproval(request.getRequiresApproval() != null ? request.getRequiresApproval() : false)
            .minLevelToPost(request.getMinLevelToPost() != null ? request.getMinLevelToPost() : 0)
            .build();

        if (request.getParentId() != null) {
            Forum parent = forumRepository.findById(request.getParentId())
                .orElseThrow(() -> new ResourceNotFoundException("Forum", "id", request.getParentId()));
            forum.setParent(parent);
        }

        forum = forumRepository.save(forum);
        eventPublisher.publishForumCreated(forum);
        log.info("Forum {} created", forum.getId());

        return mapToForumResponse(forum);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "forums", key = "#forumId")
    public ForumResponse getForum(UUID forumId) {
        Forum forum = forumRepository.findById(forumId)
            .orElseThrow(() -> new ResourceNotFoundException("Forum", "id", forumId));
        return mapToForumResponse(forum);
    }

    @Transactional(readOnly = true)
    public ForumResponse getForumBySlug(String slug) {
        Forum forum = forumRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Forum", "slug", slug));
        return mapToForumResponse(forum);
    }

    @Transactional
    @CacheEvict(value = "forums", key = "#forumId")
    public ForumResponse updateForum(UUID forumId, UpdateForumRequest request) {
        Forum forum = forumRepository.findById(forumId)
            .orElseThrow(() -> new ResourceNotFoundException("Forum", "id", forumId));

        if (request.getName() != null) {
            forum.setName(request.getName());
            forum.setSlug(generateSlug(request.getName()));
        }
        if (request.getDescription() != null) forum.setDescription(request.getDescription());
        if (request.getIcon() != null) forum.setIcon(request.getIcon());
        if (request.getColor() != null) forum.setColor(request.getColor());
        if (request.getDisplayOrder() != null) forum.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsLocked() != null) forum.setIsLocked(request.getIsLocked());
        if (request.getRequiresApproval() != null) forum.setRequiresApproval(request.getRequiresApproval());
        if (request.getMinLevelToPost() != null) forum.setMinLevelToPost(request.getMinLevelToPost());

        forum = forumRepository.save(forum);
        log.info("Forum {} updated", forumId);

        return mapToForumResponse(forum);
    }

    @Transactional
    @CacheEvict(value = "forums", key = "#forumId")
    public void deleteForum(UUID forumId) {
        Forum forum = forumRepository.findById(forumId)
            .orElseThrow(() -> new ResourceNotFoundException("Forum", "id", forumId));

        if (forum.getTopicCount() > 0) {
            throw new ValidationException("Cannot delete forum with existing topics");
        }

        forum.softDelete();
        forumRepository.save(forum);
        eventPublisher.publishForumDeleted(forum);
        log.info("Forum {} deleted", forumId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "forums", key = "'all'")
    public List<ForumResponse> getAllForums() {
        return forumRepository.findMainForums().stream()
            .map(this::mapToForumResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ForumSummaryResponse> getSubForums(UUID parentId) {
        return forumRepository.findByParentIdOrderByDisplayOrder(parentId).stream()
            .map(this::mapToForumSummary)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ForumResponse> getGroupForums(UUID groupId) {
        return forumRepository.findByGroupIdOrderByDisplayOrder(groupId).stream()
            .map(this::mapToForumResponse)
            .collect(Collectors.toList());
    }

    private String generateSlug(String input) {
        String noWhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH).replaceAll("-+", "-").replaceAll("^-|-$", "");

        String baseSlug = slug;
        int counter = 1;
        while (forumRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private ForumResponse mapToForumResponse(Forum forum) {
        ForumResponse.ForumResponseBuilder builder = ForumResponse.builder()
            .id(forum.getId())
            .parentId(forum.getParent() != null ? forum.getParent().getId() : null)
            .groupId(forum.getGroupId())
            .name(forum.getName())
            .slug(forum.getSlug())
            .description(forum.getDescription())
            .forumType(forum.getForumType())
            .icon(forum.getIcon())
            .color(forum.getColor())
            .displayOrder(forum.getDisplayOrder())
            .topicCount(forum.getTopicCount())
            .postCount(forum.getPostCount())
            .isLocked(forum.getIsLocked())
            .requiresApproval(forum.getRequiresApproval())
            .minLevelToPost(forum.getMinLevelToPost())
            .lastPostAt(forum.getLastPostAt())
            .createdAt(forum.getCreatedAt());

        // Sub-forums
        List<ForumSummaryResponse> subForums = forum.getSubForums().stream()
            .filter(f -> !f.isDeleted())
            .map(this::mapToForumSummary)
            .collect(Collectors.toList());
        builder.subForums(subForums);

        // Last topic
        if (forum.getLastTopicId() != null) {
            topicRepository.findById(forum.getLastTopicId())
                .ifPresent(topic -> builder.lastTopic(mapToTopicSummary(topic)));
        }

        return builder.build();
    }

    private ForumSummaryResponse mapToForumSummary(Forum forum) {
        return ForumSummaryResponse.builder()
            .id(forum.getId())
            .name(forum.getName())
            .slug(forum.getSlug())
            .description(forum.getDescription())
            .icon(forum.getIcon())
            .topicCount(forum.getTopicCount())
            .postCount(forum.getPostCount())
            .isLocked(forum.getIsLocked())
            .build();
    }

    private TopicSummaryResponse mapToTopicSummary(Topic topic) {
        TopicSummaryResponse.TopicSummaryResponseBuilder builder = TopicSummaryResponse.builder()
            .id(topic.getId())
            .title(topic.getTitle())
            .slug(topic.getSlug())
            .status(topic.getStatus())
            .isPinned(topic.getIsPinned())
            .isSticky(topic.getIsSticky())
            .replyCount(topic.getReplyCount())
            .viewCount(topic.getViewCount())
            .score(topic.getScore())
            .lastPostAt(topic.getLastPostAt())
            .createdAt(topic.getCreatedAt());

        try {
            UserSummary author = userServiceClient.getUserSummary(topic.getAuthorId());
            builder.author(author);
        } catch (Exception e) {
            log.warn("Failed to fetch topic author: {}", e.getMessage());
        }

        return builder.build();
    }
}
