package com.ossn.content.forum.service;

import com.ossn.content.client.UserServiceClient;
import com.ossn.content.forum.dto.request.ForumRequests.*;
import com.ossn.content.forum.dto.response.ForumResponses.*;
import com.ossn.content.dto.UserSummary;
import com.ossn.content.forum.entity.*;
import com.ossn.content.forum.entity.enums.VoteType;
import com.ossn.content.forum.event.ForumEventPublisher;
import com.ossn.content.exception.ForbiddenException;
import com.ossn.content.exception.ResourceNotFoundException;
import com.ossn.content.exception.ValidationException;
import com.ossn.content.forum.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final TopicRepository topicRepository;
    private final ForumRepository forumRepository;
    private final PostVoteRepository postVoteRepository;
    private final TopicSubscriptionRepository subscriptionRepository;
    private final UserServiceClient userServiceClient;
    private final ForumEventPublisher eventPublisher;

    @Value("${app.forum.edit-window-minutes:30}")
    private int editWindowMinutes;

    @Transactional
    public PostResponse createPost(UUID userId, CreatePostRequest request) {
        Topic topic = topicRepository.findById(request.getTopicId())
            .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", request.getTopicId()));

        if (topic.isLocked()) {
            throw new ForbiddenException("This topic is locked");
        }

        String sanitizedContent = sanitizeContent(request.getContent());

        Post post = Post.builder()
            .topic(topic)
            .authorId(userId)
            .content(sanitizedContent)
            .build();

        if (request.getParentId() != null) {
            Post parent = postRepository.findById(request.getParentId())
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", request.getParentId()));
            post.setParent(parent);
            parent.incrementReplyCount();
            postRepository.save(parent);
        }

        post = postRepository.save(post);

        // Update topic
        topic.incrementReplyCount();
        topic.setLastPostId(post.getId());
        topic.setLastPostAuthorId(userId);
        topic.setLastPostAt(LocalDateTime.now());
        topicRepository.save(topic);

        // Update forum
        Forum forum = topic.getForum();
        forum.incrementPostCount();
        forum.setLastPostAt(LocalDateTime.now());
        forumRepository.save(forum);

        eventPublisher.publishPostCreated(post);

        // Notify subscribers
        List<UUID> subscribers = subscriptionRepository.findSubscribersToNotify(topic.getId());
        subscribers.remove(userId); // Don't notify the author
        if (!subscribers.isEmpty()) {
            eventPublisher.publishTopicReplyNotification(topic, post, subscribers);
        }

        log.info("Post {} created by user {} in topic {}", post.getId(), userId, topic.getId());

        return mapToPostResponse(post, userId);
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(UUID postId, UUID currentUserId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));
        return mapToPostResponse(post, currentUserId);
    }

    @Transactional
    public PostResponse updatePost(UUID userId, UUID postId, UpdatePostRequest request) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (!post.getAuthorId().equals(userId)) {
            throw new ForbiddenException("You can only edit your own posts");
        }

        if (post.getCreatedAt().plusMinutes(editWindowMinutes).isBefore(LocalDateTime.now())) {
            throw new ValidationException("Edit window has expired");
        }

        post.setContent(sanitizeContent(request.getContent()));
        post.markAsEdited(userId);

        post = postRepository.save(post);
        log.info("Post {} updated by user {}", postId, userId);

        return mapToPostResponse(post, userId);
    }

    @Transactional
    public void deletePost(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        Topic topic = post.getTopic();
        
        if (!post.getAuthorId().equals(userId) && !topic.getAuthorId().equals(userId)) {
            throw new ForbiddenException("You cannot delete this post");
        }

        // Update parent reply count
        if (post.getParent() != null) {
            post.getParent().decrementReplyCount();
            postRepository.save(post.getParent());
        }

        // Update topic
        topic.decrementReplyCount();
        topicRepository.save(topic);

        // Update forum
        Forum forum = topic.getForum();
        forum.decrementPostCount();
        forumRepository.save(forum);

        post.softDelete();
        postRepository.save(post);

        eventPublisher.publishPostDeleted(post);
        log.info("Post {} deleted by user {}", postId, userId);
    }

    @Transactional
    public void votePost(UUID userId, UUID postId, VoteType voteType) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        postVoteRepository.findByPostIdAndUserId(postId, userId)
            .ifPresentOrElse(
                existingVote -> {
                    if (existingVote.getVoteType() == voteType) {
                        postVoteRepository.delete(existingVote);
                        if (voteType == VoteType.UPVOTE) post.setUpvoteCount(post.getUpvoteCount() - 1);
                        else post.setDownvoteCount(post.getDownvoteCount() - 1);
                    } else {
                        existingVote.setVoteType(voteType);
                        postVoteRepository.save(existingVote);
                        if (voteType == VoteType.UPVOTE) {
                            post.setUpvoteCount(post.getUpvoteCount() + 1);
                            post.setDownvoteCount(post.getDownvoteCount() - 1);
                        } else {
                            post.setDownvoteCount(post.getDownvoteCount() + 1);
                            post.setUpvoteCount(post.getUpvoteCount() - 1);
                        }
                    }
                },
                () -> {
                    postVoteRepository.save(PostVote.builder()
                        .postId(postId)
                        .userId(userId)
                        .voteType(voteType)
                        .build());
                    if (voteType == VoteType.UPVOTE) post.setUpvoteCount(post.getUpvoteCount() + 1);
                    else post.setDownvoteCount(post.getDownvoteCount() + 1);
                }
            );

        post.updateScore();
        postRepository.save(post);
    }

    @Transactional
    public PostResponse markAsSolution(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        Topic topic = post.getTopic();
        if (!topic.getAuthorId().equals(userId)) {
            throw new ForbiddenException("Only the topic author can mark solutions");
        }

        // Unmark any existing solutions
        postRepository.findSolutions(topic.getId()).forEach(p -> {
            p.setIsSolution(false);
            postRepository.save(p);
        });

        post.setIsSolution(true);
        post = postRepository.save(post);

        eventPublisher.publishSolutionMarked(post);
        log.info("Post {} marked as solution by user {}", postId, userId);

        return mapToPostResponse(post, userId);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getTopicPosts(UUID topicId, UUID currentUserId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Post> posts = postRepository.findByTopicIdOrderByCreatedAt(topicId, pageRequest);
        return posts.map(p -> mapToPostResponse(p, currentUserId));
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getUserPosts(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return postRepository.findByAuthorIdOrderByCreatedAtDesc(userId, pageRequest)
            .map(p -> mapToPostResponse(p, null));
    }

    private String sanitizeContent(String content) {
        return Jsoup.clean(content, Safelist.relaxed());
    }

    private PostResponse mapToPostResponse(Post post, UUID currentUserId) {
        PostResponse.PostResponseBuilder builder = PostResponse.builder()
            .id(post.getId())
            .topicId(post.getTopic().getId())
            .parentId(post.getParent() != null ? post.getParent().getId() : null)
            .content(post.getContent())
            .upvoteCount(post.getUpvoteCount())
            .downvoteCount(post.getDownvoteCount())
            .score(post.getScore())
            .replyCount(post.getReplyCount())
            .isSolution(post.getIsSolution())
            .isEdited(post.getIsEdited())
            .editedAt(post.getEditedAt())
            .createdAt(post.getCreatedAt())
            .updatedAt(post.getUpdatedAt());

        try {
            builder.author(userServiceClient.getUserSummary(post.getAuthorId()));
        } catch (Exception e) {
            log.warn("Failed to fetch author: {}", e.getMessage());
        }

        // Fetch nested replies (one level)
        if (post.getParent() == null) {
            List<PostResponse> replies = postRepository.findReplies(post.getId()).stream()
                .map(r -> mapToPostResponseWithoutReplies(r, currentUserId))
                .collect(Collectors.toList());
            builder.replies(replies);
        }

        if (currentUserId != null) {
            builder.isAuthor(post.getAuthorId().equals(currentUserId));
            builder.canEdit(post.getAuthorId().equals(currentUserId) && 
                post.getCreatedAt().plusMinutes(editWindowMinutes).isAfter(LocalDateTime.now()));
            builder.canDelete(post.getAuthorId().equals(currentUserId) || 
                post.getTopic().getAuthorId().equals(currentUserId));
            builder.canMarkSolution(post.getTopic().getAuthorId().equals(currentUserId));

            postVoteRepository.findByPostIdAndUserId(post.getId(), currentUserId)
                .ifPresent(vote -> builder.userVote(vote.getVoteType()));
        }

        return builder.build();
    }

    private PostResponse mapToPostResponseWithoutReplies(Post post, UUID currentUserId) {
        PostResponse.PostResponseBuilder builder = PostResponse.builder()
            .id(post.getId())
            .topicId(post.getTopic().getId())
            .parentId(post.getParent() != null ? post.getParent().getId() : null)
            .content(post.getContent())
            .upvoteCount(post.getUpvoteCount())
            .downvoteCount(post.getDownvoteCount())
            .score(post.getScore())
            .replyCount(post.getReplyCount())
            .isSolution(post.getIsSolution())
            .isEdited(post.getIsEdited())
            .editedAt(post.getEditedAt())
            .createdAt(post.getCreatedAt())
            .updatedAt(post.getUpdatedAt());

        try {
            builder.author(userServiceClient.getUserSummary(post.getAuthorId()));
        } catch (Exception e) {
            log.warn("Failed to fetch author: {}", e.getMessage());
        }

        if (currentUserId != null) {
            builder.isAuthor(post.getAuthorId().equals(currentUserId));
            builder.canEdit(post.getAuthorId().equals(currentUserId));
        }

        return builder.build();
    }
}
