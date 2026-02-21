package com.billboard.social.graph.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Block;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.FollowRepository;
import com.billboard.social.graph.repository.FriendshipRepository;
import feign.FeignException;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockService {

    private final BlockRepository blockRepository;
    private final FriendshipRepository friendshipRepository;
    private final FollowRepository followRepository;
    private final UserServiceClient userServiceClient;
    private final SocialEventPublisher eventPublisher;

    @Value("${app.blocking.max-blocked:1000}")
    private int maxBlocked;

    @Transactional
    @CacheEvict(value = {"blockedIds", "friends", "friendIds", "followStats"}, allEntries = true)
    public BlockResponse blockUser(UUID blockerId, BlockRequest request) {
        if (request.getUserId() == null) {
            throw new ValidationException("User ID is required");
        }

        UUID blockedId = request.getUserId();

        if (blockerId.equals(blockedId)) {
            throw new ValidationException("Cannot block yourself");
        }

        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new ValidationException("User already blocked");
        }

        long blockedCount = blockRepository.countByBlockerId(blockerId);
        if (blockedCount >= maxBlocked) {
            throw new ValidationException("Maximum blocked users limit reached");
        }

        friendshipRepository.findBetweenUsers(blockerId, blockedId)
                .ifPresent(friendshipRepository::delete);

        followRepository.findByFollowerIdAndFollowingId(blockerId, blockedId)
                .ifPresent(followRepository::delete);
        followRepository.findByFollowerIdAndFollowingId(blockedId, blockerId)
                .ifPresent(followRepository::delete);

        Block block = Block.builder()
                .blockerId(blockerId)
                .blockedId(blockedId)
                .reason(request.getReason())
                .blockMessages(request.getBlockMessages() != null ? request.getBlockMessages() : true)
                .blockPosts(request.getBlockPosts() != null ? request.getBlockPosts() : true)
                .blockComments(request.getBlockComments() != null ? request.getBlockComments() : true)
                .build();

        try {
            block = blockRepository.save(block);
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition detected for block from {} to {}: {}", blockerId, blockedId, e.getMessage());
            throw new ValidationException("User already blocked");
        }

        eventPublisher.publishUserBlocked(block);

        log.info("User {} blocked {}", blockerId, blockedId);
        return mapToBlockResponse(block);
    }

    @Transactional
    @CacheEvict(value = "blockedIds", allEntries = true)
    public void unblockUser(UUID blockerId, UUID blockedId) {
        Block block = blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseThrow(() -> new ValidationException("Block relationship not found"));

        blockRepository.delete(block);

        eventPublisher.publishUserUnblocked(blockerId, blockedId);

        log.info("User {} unblocked {}", blockerId, blockedId);
    }

    @Transactional(readOnly = true)
    public PageResponse<BlockResponse> getBlockedUsers(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Block> blocks = blockRepository.findByBlockerId(userId, pageRequest);
        return PageResponse.from(blocks, this::mapToBlockResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "blockedIds", key = "#userId")
    public List<UUID> getBlockedUserIds(UUID userId) {
        return blockRepository.findBlockedUserIds(userId);
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(UUID blockerId, UUID blockedId) {
        return blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UUID userId1, UUID userId2) {
        return blockRepository.isBlockedEitherWay(userId1, userId2);
    }

    private BlockResponse mapToBlockResponse(Block block) {
        UserSummary userSummary = fetchUserSummaryWithFallback(block.getBlockedId());

        return BlockResponse.builder()
                .id(block.getId())
                .blockedId(block.getBlockedId())
                .reason(block.getReason())
                .createdAt(block.getCreatedAt())
                .blockedUser(userSummary)
                .build();
    }

    private UserSummary fetchUserSummaryWithFallback(UUID userId) {
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