package com.billboard.social.graph.service;
import com.billboard.social.common.dto.UserSummary;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Reaction;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.entity.enums.ReactionType;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.graph.repository.ReactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final UserServiceClient userServiceClient;
    private final SocialEventPublisher eventPublisher;

    @Transactional
    public ReactionResponse react(UUID userId, ReactionRequest request) {
        // Check if user already reacted
        var existingReaction = reactionRepository.findByUserIdAndContentTypeAndContentId(
            userId, request.getContentType(), request.getContentId());

        Reaction reaction;
        if (existingReaction.isPresent()) {
            // Update existing reaction
            reaction = existingReaction.get();
            reaction.changeReaction(request.getReactionType());
            reaction = reactionRepository.save(reaction);
            log.info("User {} changed reaction on {} {} to {}", 
                userId, request.getContentType(), request.getContentId(), request.getReactionType());
        } else {
            // Create new reaction
            reaction = Reaction.builder()
                .userId(userId)
                .contentType(request.getContentType())
                .contentId(request.getContentId())
                .contentOwnerId(request.getContentOwnerId())
                .reactionType(request.getReactionType())
                .build();
            reaction = reactionRepository.save(reaction);

            // Publish event
            eventPublisher.publishReactionAdded(reaction);

            log.info("User {} reacted with {} on {} {}", 
                userId, request.getReactionType(), request.getContentType(), request.getContentId());
        }

        return mapToReactionResponse(reaction);
    }

    @Transactional
    public void removeReaction(UUID userId, ContentType contentType, UUID contentId) {
        Reaction reaction = reactionRepository.findByUserIdAndContentTypeAndContentId(userId, contentType, contentId)
            .orElseThrow(() -> new ResourceNotFoundException("Reaction not found"));

        reactionRepository.delete(reaction);

        // Publish event
        eventPublisher.publishReactionRemoved(reaction);

        log.info("User {} removed reaction from {} {}", userId, contentType, contentId);
    }

    @Transactional(readOnly = true)
    public Page<ReactionResponse> getReactions(ContentType contentType, UUID contentId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Reaction> reactions = reactionRepository.findByContentTypeAndContentId(contentType, contentId, pageRequest);
        return reactions.map(this::mapToReactionResponse);
    }

    @Transactional(readOnly = true)
    public Page<ReactionResponse> getReactionsByType(ContentType contentType, UUID contentId, 
                                                      ReactionType reactionType, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Reaction> reactions = reactionRepository.findByContentAndReactionType(
            contentType, contentId, reactionType, pageRequest);
        return reactions.map(this::mapToReactionResponse);
    }

    @Transactional(readOnly = true)
    public ReactionStatsResponse getReactionStats(UUID userId, ContentType contentType, UUID contentId) {
        long totalCount = reactionRepository.countByContentTypeAndContentId(contentType, contentId);

        List<Object[]> countByType = reactionRepository.countByContentGroupedByReactionType(contentType, contentId);
        Map<ReactionType, Long> countByTypeMap = new HashMap<>();
        for (Object[] row : countByType) {
            countByTypeMap.put((ReactionType) row[0], (Long) row[1]);
        }

        boolean userReacted = false;
        ReactionType userReactionType = null;

        if (userId != null) {
            var userReaction = reactionRepository.findByUserIdAndContentTypeAndContentId(userId, contentType, contentId);
            if (userReaction.isPresent()) {
                userReacted = true;
                userReactionType = userReaction.get().getReactionType();
            }
        }

        return ReactionStatsResponse.builder()
            .contentType(contentType)
            .contentId(contentId)
            .totalCount(totalCount)
            .countByType(countByTypeMap)
            .userReacted(userReacted)
            .userReactionType(userReactionType)
            .build();
    }

    @Transactional(readOnly = true)
    public boolean hasUserReacted(UUID userId, ContentType contentType, UUID contentId) {
        return reactionRepository.existsByUserIdAndContentTypeAndContentId(userId, contentType, contentId);
    }

    private ReactionResponse mapToReactionResponse(Reaction reaction) {
        UserSummary userSummary = userServiceClient.getUserSummary(reaction.getUserId());

        return ReactionResponse.builder()
            .id(reaction.getId())
            .userId(reaction.getUserId())
            .contentType(reaction.getContentType())
            .contentId(reaction.getContentId())
            .reactionType(reaction.getReactionType())
            .createdAt(reaction.getCreatedAt())
            .user(userSummary)
            .build();
    }
}
