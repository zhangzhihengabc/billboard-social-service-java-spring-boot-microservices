package com.ossn.content.feed.service;

import com.ossn.content.client.UserServiceClient;
import com.ossn.content.feed.dto.request.FeedRequests.*;
import com.ossn.content.feed.dto.response.FeedResponses.*;
import com.ossn.content.dto.UserSummary;
import com.ossn.content.feed.entity.Post;
import com.ossn.content.feed.entity.PostReaction;
import com.ossn.content.feed.entity.enums.ReactionType;
import com.ossn.content.feed.event.FeedEventPublisher;
import com.ossn.content.exception.ResourceNotFoundException;
import com.ossn.content.exception.ValidationException;
import com.ossn.content.feed.repository.PostReactionRepository;
import com.ossn.content.feed.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReactionService {

    private final PostReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final UserServiceClient userServiceClient;
    private final FeedEventPublisher eventPublisher;

    @Transactional
    public void reactToPost(UUID userId, UUID postId, ReactRequest request) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (!post.getAllowReactions()) {
            throw new ValidationException("Reactions are disabled for this post");
        }

        Optional<PostReaction> existingReaction = reactionRepository.findByPostIdAndUserId(postId, userId);

        if (existingReaction.isPresent()) {
            PostReaction reaction = existingReaction.get();
            ReactionType oldType = reaction.getReactionType();

            if (oldType == request.getReactionType()) {
                // Same reaction = remove it
                reactionRepository.delete(reaction);
                updatePostReactionCount(post, oldType, -1);
                log.info("User {} removed {} reaction from post {}", userId, oldType, postId);
            } else {
                // Different reaction = update it
                reaction.setReactionType(request.getReactionType());
                reactionRepository.save(reaction);
                updatePostReactionCount(post, oldType, -1);
                updatePostReactionCount(post, request.getReactionType(), 1);
                log.info("User {} changed reaction from {} to {} on post {}", 
                    userId, oldType, request.getReactionType(), postId);
            }
        } else {
            // New reaction
            PostReaction reaction = PostReaction.builder()
                .post(post)
                .userId(userId)
                .reactionType(request.getReactionType())
                .build();
            reactionRepository.save(reaction);
            updatePostReactionCount(post, request.getReactionType(), 1);

            eventPublisher.publishReactionAdded(post, userId, request.getReactionType());
            log.info("User {} reacted {} to post {}", userId, request.getReactionType(), postId);
        }

        postRepository.save(post);
    }

    @Transactional
    public void removeReaction(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        PostReaction reaction = reactionRepository.findByPostIdAndUserId(postId, userId)
            .orElseThrow(() -> new ValidationException("You have not reacted to this post"));

        updatePostReactionCount(post, reaction.getReactionType(), -1);
        reactionRepository.delete(reaction);
        postRepository.save(post);

        log.info("User {} removed reaction from post {}", userId, postId);
    }

    @Transactional(readOnly = true)
    public Page<ReactionResponse> getPostReactions(UUID postId, ReactionType type, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);

        Page<PostReaction> reactions;
        if (type != null) {
            reactions = reactionRepository.findByPostIdAndReactionType(postId, type, pageRequest);
        } else {
            reactions = reactionRepository.findByPostId(postId, pageRequest);
        }

        return reactions.map(this::mapToReactionResponse);
    }

    @Transactional(readOnly = true)
    public Map<ReactionType, Integer> getReactionCounts(UUID postId) {
        List<Object[]> results = reactionRepository.countReactionsByType(postId);
        Map<ReactionType, Integer> counts = new EnumMap<>(ReactionType.class);

        for (Object[] result : results) {
            ReactionType type = (ReactionType) result[0];
            Long count = (Long) result[1];
            counts.put(type, count.intValue());
        }

        return counts;
    }

    @Transactional(readOnly = true)
    public ReactionType getUserReaction(UUID userId, UUID postId) {
        return reactionRepository.findByPostIdAndUserId(postId, userId)
            .map(PostReaction::getReactionType)
            .orElse(null);
    }

    private void updatePostReactionCount(Post post, ReactionType type, int delta) {
        switch (type) {
            case LIKE -> {
                if (delta > 0) post.incrementLikeCount();
                else post.decrementLikeCount();
            }
            case LOVE -> {
                if (delta > 0) post.incrementLoveCount();
                else post.decrementLoveCount();
            }
            default -> {
                // For other reaction types, just use like count as a fallback
                if (delta > 0) post.incrementLikeCount();
                else post.decrementLikeCount();
            }
        }
    }

    private ReactionResponse mapToReactionResponse(PostReaction reaction) {
        UserSummary user = null;
        try {
            user = userServiceClient.getUserSummary(reaction.getUserId());
        } catch (Exception e) {
            log.warn("Failed to fetch user info for reaction: {}", e.getMessage());
        }

        return ReactionResponse.builder()
            .id(reaction.getId())
            .postId(reaction.getPost().getId())
            .user(user)
            .reactionType(reaction.getReactionType())
            .createdAt(reaction.getCreatedAt())
            .build();
    }
}
