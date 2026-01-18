package com.billboard.social.graph.service;
import com.billboard.social.common.dto.UserSummary;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Block;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.FollowRepository;
import com.billboard.social.graph.repository.FriendshipRepository;
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
    @CacheEvict(value = {"blockedIds", "friends", "followStats"}, allEntries = true)
    public BlockResponse blockUser(UUID blockerId, BlockRequest request) {
        UUID blockedId = request.getUserId();

        // Validate not self
        if (blockerId.equals(blockedId)) {
            throw new ValidationException("Cannot block yourself");
        }

        // Check if already blocked
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new ValidationException("User already blocked");
        }

        // Check max blocked limit
        long blockedCount = blockRepository.countByBlockerId(blockerId);
        if (blockedCount >= maxBlocked) {
            throw new ValidationException("Maximum blocked users limit reached");
        }

        // Remove any existing friendship
        friendshipRepository.findBetweenUsers(blockerId, blockedId)
            .ifPresent(f -> {
                f.softDelete();
                friendshipRepository.save(f);
            });

        // Remove any follow relationships
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

        block = blockRepository.save(block);

        // Publish event
        eventPublisher.publishUserBlocked(block);

        log.info("User {} blocked {}", blockerId, blockedId);
        return mapToBlockResponse(block);
    }

    @Transactional
    @CacheEvict(value = "blockedIds", allEntries = true)
    public void unblockUser(UUID blockerId, UUID blockedId) {
        Block block = blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
            .orElseThrow(() -> new ResourceNotFoundException("Block relationship not found"));

        blockRepository.delete(block);

        // Publish event
        eventPublisher.publishUserUnblocked(blockerId, blockedId);

        log.info("User {} unblocked {}", blockerId, blockedId);
    }

    @Transactional(readOnly = true)
    public Page<BlockResponse> getBlockedUsers(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Block> blocks = blockRepository.findByBlockerId(userId, pageRequest);
        return blocks.map(this::mapToBlockResponse);
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
        UserSummary userSummary = userServiceClient.getUserSummary(block.getBlockedId());

        return BlockResponse.builder()
            .id(block.getId())
            .blockedId(block.getBlockedId())
            .reason(block.getReason())
            .createdAt(block.getCreatedAt())
            .blockedUser(userSummary)
            .build();
    }
}
