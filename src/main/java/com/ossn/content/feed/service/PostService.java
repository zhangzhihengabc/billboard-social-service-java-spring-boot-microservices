package com.ossn.content.feed.service;

import com.ossn.content.client.SocialGraphClient;
import com.ossn.content.client.UserServiceClient;
import com.ossn.content.feed.dto.request.FeedRequests.*;
import com.ossn.content.feed.dto.response.FeedResponses.*;
import com.ossn.content.dto.UserSummary;
import com.ossn.content.feed.entity.Post;
import com.ossn.content.feed.entity.PostMedia;
import com.ossn.content.feed.entity.PostMention;
import com.ossn.content.feed.entity.PostReaction;
import com.ossn.content.feed.entity.enums.PostType;
import com.ossn.content.feed.entity.enums.ReactionType;
import com.ossn.content.feed.event.FeedEventPublisher;
import com.ossn.content.exception.ForbiddenException;
import com.ossn.content.exception.ResourceNotFoundException;
import com.ossn.content.exception.ValidationException;
import com.ossn.content.feed.repository.PostReactionRepository;
import com.ossn.content.feed.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final PostReactionRepository reactionRepository;
    private final UserServiceClient userServiceClient;
    private final SocialGraphClient socialGraphClient;
    private final FeedEventPublisher eventPublisher;

    @Transactional
    public PostResponse createPost(UUID userId, CreatePostRequest request) {
        if (request.getContent() == null && 
            (request.getMediaItems() == null || request.getMediaItems().isEmpty()) &&
            request.getLinkUrl() == null) {
            throw new ValidationException("Post must have content, media, or a link");
        }

        Post post = Post.builder()
            .authorId(userId)
            .wallOwnerId(request.getWallOwnerId() != null ? request.getWallOwnerId() : userId)
            .groupId(request.getGroupId())
            .eventId(request.getEventId())
            .postType(request.getPostType())
            .visibility(request.getVisibility())
            .content(request.getContent())
            .linkUrl(request.getLinkUrl())
            .feeling(request.getFeeling())
            .location(request.getLocation())
            .scheduledAt(request.getScheduledAt())
            .allowComments(request.getAllowComments())
            .allowReactions(request.getAllowReactions())
            .build();

        if (request.getScheduledAt() == null) {
            post.setPublishedAt(LocalDateTime.now());
        }

        // Add media items
        if (request.getMediaItems() != null) {
            int order = 0;
            for (MediaItem item : request.getMediaItems()) {
                PostMedia media = PostMedia.builder()
                    .post(post)
                    .mediaType(item.getMediaType())
                    .url(item.getUrl())
                    .thumbnailUrl(item.getThumbnailUrl())
                    .width(item.getWidth())
                    .height(item.getHeight())
                    .durationSeconds(item.getDurationSeconds())
                    .fileSize(item.getFileSize())
                    .altText(item.getAltText())
                    .displayOrder(order++)
                    .build();
                post.getMediaItems().add(media);
            }

            if (post.getPostType() == PostType.STATUS && !request.getMediaItems().isEmpty()) {
                String firstType = request.getMediaItems().get(0).getMediaType();
                if ("IMAGE".equalsIgnoreCase(firstType)) {
                    post.setPostType(PostType.PHOTO);
                } else if ("VIDEO".equalsIgnoreCase(firstType)) {
                    post.setPostType(PostType.VIDEO);
                }
            }
        }

        // Add mentions
        if (request.getMentionedUserIds() != null) {
            for (UUID mentionedUserId : request.getMentionedUserIds()) {
                PostMention mention = PostMention.builder()
                    .post(post)
                    .mentionedUserId(mentionedUserId)
                    .build();
                post.getMentions().add(mention);
            }
        }

        post = postRepository.save(post);

        eventPublisher.publishPostCreated(post);

        log.info("Post {} created by user {}", post.getId(), userId);
        return mapToPostResponse(post, userId);
    }

    @Transactional
    @CacheEvict(value = "posts", key = "#postId")
    public PostResponse updatePost(UUID userId, UUID postId, UpdatePostRequest request) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (!post.getAuthorId().equals(userId)) {
            throw new ForbiddenException("You can only edit your own posts");
        }

        if (request.getContent() != null) post.setContent(request.getContent());
        if (request.getVisibility() != null) post.setVisibility(request.getVisibility());
        if (request.getFeeling() != null) post.setFeeling(request.getFeeling());
        if (request.getLocation() != null) post.setLocation(request.getLocation());
        if (request.getAllowComments() != null) post.setAllowComments(request.getAllowComments());
        if (request.getAllowReactions() != null) post.setAllowReactions(request.getAllowReactions());

        post = postRepository.save(post);

        log.info("Post {} updated by user {}", postId, userId);
        return mapToPostResponse(post, userId);
    }

    @Transactional
    @CacheEvict(value = "posts", key = "#postId")
    public void deletePost(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        boolean canDelete = post.getAuthorId().equals(userId) ||
            (post.getWallOwnerId() != null && post.getWallOwnerId().equals(userId));

        if (!canDelete) {
            throw new ForbiddenException("You cannot delete this post");
        }

        post.softDelete();
        postRepository.save(post);

        eventPublisher.publishPostDeleted(post);

        log.info("Post {} deleted by user {}", postId, userId);
    }

    @Transactional
    public PostResponse sharePost(UUID userId, SharePostRequest request) {
        Post originalPost = postRepository.findById(request.getOriginalPostId())
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", request.getOriginalPostId()));

        Post sharedPost = Post.builder()
            .authorId(userId)
            .wallOwnerId(userId)
            .postType(PostType.SHARED)
            .visibility(request.getVisibility())
            .content(request.getComment())
            .sharedPostId(originalPost.getId())
            .publishedAt(LocalDateTime.now())
            .build();

        sharedPost = postRepository.save(sharedPost);

        originalPost.incrementShareCount();
        postRepository.save(originalPost);

        eventPublisher.publishPostShared(sharedPost, originalPost);

        log.info("Post {} shared by user {}", originalPost.getId(), userId);
        return mapToPostResponse(sharedPost, userId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "posts", key = "#postId")
    public PostResponse getPost(UUID postId, UUID currentUserId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        checkPostVisibility(post, currentUserId);

        return mapToPostResponse(post, currentUserId);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getUserFeed(UUID userId, int page, int size) {
        // Get friend IDs from social-graph-service
        List<UUID> friendIds = socialGraphClient.getFriendIds(userId);
        friendIds.add(userId); // Include own posts

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Post> posts = postRepository.findFeedPosts(friendIds, pageRequest);

        return posts.map(post -> mapToPostResponse(post, userId));
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getPublicFeed(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Post> posts = postRepository.findPublicFeed(pageRequest);
        return posts.map(post -> mapToPostResponse(post, null));
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getTrendingPosts(int page, int size) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Post> posts = postRepository.findTrendingPosts(since, pageRequest);
        return posts.map(post -> mapToPostResponse(post, null));
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getUserPosts(UUID userId, UUID currentUserId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Post> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(userId, pageRequest);
        return posts.map(post -> mapToPostResponse(post, currentUserId));
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getWallPosts(UUID wallOwnerId, UUID currentUserId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Post> posts = postRepository.findByWallOwnerIdOrderByCreatedAtDesc(wallOwnerId, pageRequest);
        return posts.map(post -> mapToPostResponse(post, currentUserId));
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getGroupPosts(UUID groupId, UUID currentUserId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Post> posts = postRepository.findByGroupIdOrderByCreatedAtDesc(groupId, pageRequest);
        return posts.map(post -> mapToPostResponse(post, currentUserId));
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> searchPosts(String query, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Post> posts = postRepository.searchPosts(query, pageRequest);
        return posts.map(post -> mapToPostResponse(post, null));
    }

    @Transactional
    @CacheEvict(value = "posts", key = "#postId")
    public void pinPost(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (!post.getWallOwnerId().equals(userId)) {
            throw new ForbiddenException("You can only pin posts on your own wall");
        }

        post.setIsPinned(true);
        postRepository.save(post);
    }

    @Transactional
    @CacheEvict(value = "posts", key = "#postId")
    public void unpinPost(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (!post.getWallOwnerId().equals(userId)) {
            throw new ForbiddenException("You can only unpin posts on your own wall");
        }

        post.setIsPinned(false);
        postRepository.save(post);
    }

    private void checkPostVisibility(Post post, UUID currentUserId) {
        switch (post.getVisibility()) {
            case ONLY_ME -> {
                if (!post.getAuthorId().equals(currentUserId)) {
                    throw new ForbiddenException("This post is private");
                }
            }
            case FRIENDS -> {
                if (currentUserId == null || 
                    (!post.getAuthorId().equals(currentUserId) && 
                     !socialGraphClient.areFriends(post.getAuthorId(), currentUserId))) {
                    throw new ForbiddenException("This post is only visible to friends");
                }
            }
            default -> {}
        }
    }

    private PostResponse mapToPostResponse(Post post, UUID currentUserId) {
        PostResponse.PostResponseBuilder builder = PostResponse.builder()
            .id(post.getId())
            .wallOwnerId(post.getWallOwnerId())
            .groupId(post.getGroupId())
            .postType(post.getPostType())
            .visibility(post.getVisibility())
            .content(post.getContent())
            .linkUrl(post.getLinkUrl())
            .linkTitle(post.getLinkTitle())
            .linkDescription(post.getLinkDescription())
            .linkImage(post.getLinkImage())
            .likeCount(post.getLikeCount())
            .loveCount(post.getLoveCount())
            .commentCount(post.getCommentCount())
            .shareCount(post.getShareCount())
            .viewCount(post.getViewCount())
            .isPinned(post.getIsPinned())
            .isHighlighted(post.getIsHighlighted())
            .allowComments(post.getAllowComments())
            .allowReactions(post.getAllowReactions())
            .feeling(post.getFeeling())
            .location(post.getLocation())
            .createdAt(post.getCreatedAt())
            .updatedAt(post.getUpdatedAt());

        // Get author info
        try {
            UserSummary author = userServiceClient.getUserSummary(post.getAuthorId());
            builder.author(author);
        } catch (Exception e) {
            log.warn("Failed to fetch author info: {}", e.getMessage());
        }

        // Map media items
        if (!post.getMediaItems().isEmpty()) {
            List<MediaResponse> mediaResponses = post.getMediaItems().stream()
                .sorted(Comparator.comparingInt(PostMedia::getDisplayOrder))
                .map(m -> MediaResponse.builder()
                    .id(m.getId())
                    .mediaType(m.getMediaType())
                    .url(m.getUrl())
                    .thumbnailUrl(m.getThumbnailUrl())
                    .width(m.getWidth())
                    .height(m.getHeight())
                    .durationSeconds(m.getDurationSeconds())
                    .altText(m.getAltText())
                    .displayOrder(m.getDisplayOrder())
                    .build())
                .collect(Collectors.toList());
            builder.mediaItems(mediaResponses);
        }

        // Get shared post if applicable
        if (post.getSharedPostId() != null) {
            postRepository.findById(post.getSharedPostId())
                .ifPresent(sharedPost -> builder.sharedPost(mapToPostResponse(sharedPost, currentUserId)));
        }

        // Current user context
        if (currentUserId != null) {
            builder.isAuthor(post.getAuthorId().equals(currentUserId));
            builder.canEdit(post.getAuthorId().equals(currentUserId));
            builder.canDelete(post.getAuthorId().equals(currentUserId) || 
                (post.getWallOwnerId() != null && post.getWallOwnerId().equals(currentUserId)));

            reactionRepository.findByPostIdAndUserId(post.getId(), currentUserId)
                .ifPresent(reaction -> builder.userReaction(reaction.getReactionType()));
        }

        return builder.build();
    }
}
