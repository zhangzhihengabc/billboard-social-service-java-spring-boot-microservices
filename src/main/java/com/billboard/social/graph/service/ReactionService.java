package com.billboard.social.graph.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Reaction;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.entity.enums.ReactionType;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.repository.ReactionRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    public ReactionResponse react(Long userId, ReactionRequest request) {
        if (request.getContentId() == null) {
            throw new ValidationException("Content ID is required");
        }
        if (request.getContentType() == null) {
            throw new ValidationException("Content type is required");
        }
        if (request.getReactionType() == null) {
            throw new ValidationException("Reaction type is required");
        }

        var existingReaction = reactionRepository.findByUserIdAndContentTypeAndContentId(
                userId, request.getContentType(), request.getContentId());

        Reaction reaction;
        if (existingReaction.isPresent()) {
            reaction = existingReaction.get();
            reaction.changeReaction(request.getReactionType());
            reaction = reactionRepository.save(reaction);
            log.info("User {} changed reaction on {} {} to {}",
                    userId, request.getContentType(), request.getContentId(), request.getReactionType());
        } else {
            reaction = Reaction.builder()
                    .userId(userId)
                    .contentType(request.getContentType())
                    .contentId(request.getContentId())
                    .contentOwnerId(request.getContentOwnerId())
                    .reactionType(request.getReactionType())
                    .build();

            try {
                reaction = reactionRepository.save(reaction);
            } catch (DataIntegrityViolationException e) {
                log.warn("Race condition detected for reaction from {} on {} {}: {}",
                        userId, request.getContentType(), request.getContentId(), e.getMessage());
                throw new ValidationException("Reaction already exists");
            }

            eventPublisher.publishReactionAdded(reaction);

            log.info("User {} reacted with {} on {} {}",
                    userId, request.getReactionType(), request.getContentType(), request.getContentId());
        }

        return mapToReactionResponse(reaction);
    }

    @Transactional
    public void removeReaction(Long userId, ContentType contentType, UUID contentId) {
        Reaction reaction = reactionRepository.findByUserIdAndContentTypeAndContentId(userId, contentType, contentId)
                .orElseThrow(() -> new ValidationException("Reaction not found"));

        reactionRepository.delete(reaction);

        eventPublisher.publishReactionRemoved(reaction);

        log.info("User {} removed reaction from {} {}", userId, contentType, contentId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReactionResponse> getReactions(ContentType contentType, UUID contentId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Reaction> reactions = reactionRepository.findByContentTypeAndContentId(contentType, contentId, pageRequest);
        return PageResponse.from(reactions, this::mapToReactionResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReactionResponse> getReactionsByType(ContentType contentType, UUID contentId,
                                                             ReactionType reactionType, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Reaction> reactions = reactionRepository.findByContentAndReactionType(
                contentType, contentId, reactionType, pageRequest);
        return PageResponse.from(reactions, this::mapToReactionResponse);
    }

    @Transactional(readOnly = true)
    public ReactionStatsResponse getReactionStats(Long userId, ContentType contentType, UUID contentId) {
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
    public boolean hasUserReacted(Long userId, ContentType contentType, UUID contentId) {
        return reactionRepository.existsByUserIdAndContentTypeAndContentId(userId, contentType, contentId);
    }

    private ReactionResponse mapToReactionResponse(Reaction reaction) {
        UserSummary userSummary = fetchUserSummaryWithFallback(reaction.getUserId());

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

    private UserSummary fetchUserSummaryWithFallback(Long userId) {
        try {
            UserSummary summary = userServiceClient.getUserSummary(userId);
            if (summary != null) {
                return summary;
            }
            log.warn("User summary returned null for userId: {}", userId);
        } catch (FeignException.NotFound e) {
            log.warn("User not found in identity-service: {}", userId);
        } catch (FeignException e) {
            log.warn("Identity service unavailable for userId {}: Status {}", userId, e.status());
        } catch (Exception e) {
            log.warn("Failed to fetch user summary for userId {}: {} - {}",
                    userId, e.getClass().getSimpleName(), e.getMessage());
        }

        return UserSummary.builder()
                .id(userId)
                .username("Unknown")
                .email("unknown@gmail.com")
                .build();
    }
}