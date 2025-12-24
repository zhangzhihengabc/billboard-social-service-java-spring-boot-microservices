package com.ossn.content.forum.service;

import com.ossn.content.forum.client.UserServiceClient;
import com.ossn.content.forum.dto.request.ForumRequests.*;
import com.ossn.content.forum.dto.response.ForumResponses.*;
import com.ossn.content.forum.entity.Forum;
import com.ossn.content.forum.entity.Topic;
import com.ossn.content.forum.entity.TopicSubscription;
import com.ossn.content.forum.entity.TopicVote;
import com.ossn.content.forum.entity.enums.TopicStatus;
import com.ossn.content.forum.entity.enums.VoteType;
import com.ossn.content.forum.event.ForumEventPublisher;
import com.ossn.content.forum.exception.ForbiddenException;
import com.ossn.content.forum.exception.ResourceNotFoundException;
import com.ossn.content.forum.exception.ValidationException;
import com.ossn.content.forum.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicService {

    private final TopicRepository topicRepository;
    private final ForumRepository forumRepository;
    private final TopicVoteRepository topicVoteRepository;
    private final TopicSubscriptionRepository subscriptionRepository;
    private final UserServiceClient userServiceClient;
    private final ForumEventPublisher eventPublisher;

    @Value("${app.forum.edit-window-minutes:30}")
    private int editWindowMinutes;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    @Transactional
    public TopicResponse createTopic(UUID userId, CreateTopicRequest request) {
        Forum forum = forumRepository.findById(request.getForumId())
            .orElseThrow(() -> new ResourceNotFoundException("Forum", "id", request.getForumId()));

        if (forum.getIsLocked()) {
            throw new ForbiddenException("This forum is locked");
        }

        String sanitizedContent = sanitizeContent(request.getContent());
        String slug = generateSlug(forum.getId(), request.getTitle());

        Topic topic = Topic.builder()
            .forum(forum)
            .authorId(userId)
            .title(request.getTitle())
            .slug(slug)
            .content(sanitizedContent)
            .tags(request.getTags())
            .isPinned(request.getIsPinned() != null ? request.getIsPinned() : false)
            .isAnnouncement(request.getIsAnnouncement() != null ? request.getIsAnnouncement() : false)
            .lastPostAt(LocalDateTime.now())
            .lastPostAuthorId(userId)
            .build();

        topic = topicRepository.save(topic);

        // Update forum
        forum.incrementTopicCount();
        forum.setLastTopicId(topic.getId());
        forum.setLastPostAt(LocalDateTime.now());
        forumRepository.save(forum);

        // Auto-subscribe author
        subscriptionRepository.save(TopicSubscription.builder()
            .topicId(topic.getId())
            .userId(userId)
            .build());

        eventPublisher.publishTopicCreated(topic);
        log.info("Topic {} created by user {}", topic.getId(), userId);

        return mapToTopicResponse(topic, userId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "topics", key = "#topicId")
    public TopicResponse getTopic(UUID topicId, UUID currentUserId) {
        Topic topic = topicRepository.findById(topicId)
            .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));

        topic.incrementViewCount();
        topicRepository.save(topic);

        return mapToTopicResponse(topic, currentUserId);
    }

    @Transactional(readOnly = true)
    public TopicResponse getTopicBySlug(UUID forumId, String slug, UUID currentUserId) {
        Topic topic = topicRepository.findByForumIdAndSlug(forumId, slug)
            .orElseThrow(() -> new ResourceNotFoundException("Topic", "slug", slug));

        topic.incrementViewCount();
        topicRepository.save(topic);

        return mapToTopicResponse(topic, currentUserId);
    }

    @Transactional
    @CacheEvict(value = "topics", key = "#topicId")
    public TopicResponse updateTopic(UUID userId, UUID topicId, UpdateTopicRequest request) {
        Topic topic = topicRepository.findById(topicId)
            .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));

        if (!topic.getAuthorId().equals(userId)) {
            throw new ForbiddenException("You can only edit your own topics");
        }

        if (topic.getCreatedAt().plusMinutes(editWindowMinutes).isBefore(LocalDateTime.now())) {
            throw new ValidationException("Edit window has expired");
        }

        if (request.getTitle() != null) {
            topic.setTitle(request.getTitle());
            topic.setSlug(generateSlug(topic.getForum().getId(), request.getTitle()));
        }
        if (request.getContent() != null) {
            topic.setContent(sanitizeContent(request.getContent()));
        }
        if (request.getTags() != null) {
            topic.setTags(request.getTags());
        }

        topic.setIsEdited(true);
        topic.setEditedAt(LocalDateTime.now());
        topic.setEditedBy(userId);

        topic = topicRepository.save(topic);
        log.info("Topic {} updated by user {}", topicId, userId);

        return mapToTopicResponse(topic, userId);
    }

    @Transactional
    @CacheEvict(value = "topics", key = "#topicId")
    public void deleteTopic(UUID userId, UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
            .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));

        if (!topic.getAuthorId().equals(userId)) {
            throw new ForbiddenException("You can only delete your own topics");
        }

        Forum forum = topic.getForum();
        forum.decrementTopicCount();
        forumRepository.save(forum);

        topic.softDelete();
        topicRepository.save(topic);

        eventPublisher.publishTopicDeleted(topic);
        log.info("Topic {} deleted by user {}", topicId, userId);
    }

    @Transactional
    @CacheEvict(value = "topics", key = "#topicId")
    public TopicResponse lockTopic(UUID userId, UUID topicId, LockTopicRequest request) {
        Topic topic = topicRepository.findById(topicId)
            .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));

        topic.setStatus(TopicStatus.LOCKED);
        topic.setLockedAt(LocalDateTime.now());
        topic.setLockedBy(userId);
        topic.setLockReason(request.getReason());

        topic = topicRepository.save(topic);
        log.info("Topic {} locked by user {}", topicId, userId);

        return mapToTopicResponse(topic, userId);
    }

    @Transactional
    @CacheEvict(value = "topics", key = "#topicId")
    public TopicResponse unlockTopic(UUID userId, UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
            .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));

        topic.setStatus(TopicStatus.OPEN);
        topic.setLockedAt(null);
        topic.setLockedBy(null);
        topic.setLockReason(null);

        topic = topicRepository.save(topic);
        log.info("Topic {} unlocked by user {}", topicId, userId);

        return mapToTopicResponse(topic, userId);
    }

    @Transactional
    public void voteTopic(UUID userId, UUID topicId, VoteType voteType) {
        Topic topic = topicRepository.findById(topicId)
            .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));

        topicVoteRepository.findByTopicIdAndUserId(topicId, userId)
            .ifPresentOrElse(
                existingVote -> {
                    if (existingVote.getVoteType() == voteType) {
                        // Remove vote
                        topicVoteRepository.delete(existingVote);
                        if (voteType == VoteType.UPVOTE) topic.setUpvoteCount(topic.getUpvoteCount() - 1);
                        else topic.setDownvoteCount(topic.getDownvoteCount() - 1);
                    } else {
                        // Change vote
                        existingVote.setVoteType(voteType);
                        topicVoteRepository.save(existingVote);
                        if (voteType == VoteType.UPVOTE) {
                            topic.setUpvoteCount(topic.getUpvoteCount() + 1);
                            topic.setDownvoteCount(topic.getDownvoteCount() - 1);
                        } else {
                            topic.setDownvoteCount(topic.getDownvoteCount() + 1);
                            topic.setUpvoteCount(topic.getUpvoteCount() - 1);
                        }
                    }
                },
                () -> {
                    // New vote
                    topicVoteRepository.save(TopicVote.builder()
                        .topicId(topicId)
                        .userId(userId)
                        .voteType(voteType)
                        .build());
                    if (voteType == VoteType.UPVOTE) topic.setUpvoteCount(topic.getUpvoteCount() + 1);
                    else topic.setDownvoteCount(topic.getDownvoteCount() + 1);
                }
            );

        topic.updateScore();
        topicRepository.save(topic);
    }

    @Transactional
    public void subscribeTopic(UUID userId, UUID topicId) {
        if (!topicRepository.existsById(topicId)) {
            throw new ResourceNotFoundException("Topic", "id", topicId);
        }

        if (!subscriptionRepository.existsByTopicIdAndUserId(topicId, userId)) {
            subscriptionRepository.save(TopicSubscription.builder()
                .topicId(topicId)
                .userId(userId)
                .build());
        }
    }

    @Transactional
    public void unsubscribeTopic(UUID userId, UUID topicId) {
        subscriptionRepository.deleteByTopicIdAndUserId(topicId, userId);
    }

    @Transactional(readOnly = true)
    public Page<TopicSummaryResponse> getForumTopics(UUID forumId, UUID currentUserId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Topic> topics = topicRepository.findActiveTopics(forumId, pageRequest);
        return topics.map(t -> mapToTopicSummary(t));
    }

    @Transactional(readOnly = true)
    public Page<TopicSummaryResponse> getRecentTopics(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return topicRepository.findRecentTopics(pageRequest).map(this::mapToTopicSummary);
    }

    @Transactional(readOnly = true)
    public Page<TopicSummaryResponse> getTrendingTopics(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        return topicRepository.findTrendingTopics(since, pageRequest).map(this::mapToTopicSummary);
    }

    @Transactional(readOnly = true)
    public Page<TopicSummaryResponse> searchTopics(String query, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return topicRepository.searchTopics(query, pageRequest).map(this::mapToTopicSummary);
    }

    @Transactional(readOnly = true)
    public Page<TopicSummaryResponse> getUserTopics(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return topicRepository.findByAuthorIdOrderByCreatedAtDesc(userId, pageRequest).map(this::mapToTopicSummary);
    }

    private String sanitizeContent(String content) {
        return Jsoup.clean(content, Safelist.relaxed());
    }

    private String generateSlug(UUID forumId, String input) {
        String noWhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH).replaceAll("-+", "-").replaceAll("^-|-$", "");

        if (slug.length() > 100) slug = slug.substring(0, 100);

        String baseSlug = slug;
        int counter = 1;
        while (topicRepository.findByForumIdAndSlug(forumId, slug).isPresent()) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private TopicResponse mapToTopicResponse(Topic topic, UUID currentUserId) {
        TopicResponse.TopicResponseBuilder builder = TopicResponse.builder()
            .id(topic.getId())
            .forumId(topic.getForum().getId())
            .forumName(topic.getForum().getName())
            .forumSlug(topic.getForum().getSlug())
            .title(topic.getTitle())
            .slug(topic.getSlug())
            .content(topic.getContent())
            .status(topic.getStatus())
            .isPinned(topic.getIsPinned())
            .isSticky(topic.getIsSticky())
            .isAnnouncement(topic.getIsAnnouncement())
            .isFeatured(topic.getIsFeatured())
            .replyCount(topic.getReplyCount())
            .viewCount(topic.getViewCount())
            .upvoteCount(topic.getUpvoteCount())
            .downvoteCount(topic.getDownvoteCount())
            .score(topic.getScore())
            .tags(topic.getTags())
            .lastPostAt(topic.getLastPostAt())
            .createdAt(topic.getCreatedAt())
            .updatedAt(topic.getUpdatedAt())
            .isEdited(topic.getIsEdited())
            .editedAt(topic.getEditedAt())
            .lockedAt(topic.getLockedAt())
            .lockReason(topic.getLockReason());

        try {
            builder.author(userServiceClient.getUserSummary(topic.getAuthorId()));
            if (topic.getLastPostAuthorId() != null) {
                builder.lastPostAuthor(userServiceClient.getUserSummary(topic.getLastPostAuthorId()));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user info: {}", e.getMessage());
        }

        if (currentUserId != null) {
            builder.isAuthor(topic.getAuthorId().equals(currentUserId));
            builder.canEdit(topic.getAuthorId().equals(currentUserId) && 
                topic.getCreatedAt().plusMinutes(editWindowMinutes).isAfter(LocalDateTime.now()));
            builder.canDelete(topic.getAuthorId().equals(currentUserId));
            builder.isSubscribed(subscriptionRepository.existsByTopicIdAndUserId(topic.getId(), currentUserId));

            topicVoteRepository.findByTopicIdAndUserId(topic.getId(), currentUserId)
                .ifPresent(vote -> builder.userVote(vote.getVoteType()));
        }

        return builder.build();
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
            builder.author(userServiceClient.getUserSummary(topic.getAuthorId()));
        } catch (Exception e) {
            log.warn("Failed to fetch author: {}", e.getMessage());
        }

        return builder.build();
    }
}
