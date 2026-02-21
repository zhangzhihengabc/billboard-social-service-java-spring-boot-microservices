package com.billboard.social.graph.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Follow;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.FollowRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowService {

    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final UserServiceClient userServiceClient;
    private final SocialEventPublisher eventPublisher;

    @Value("${social.follow.max-following:5000}")
    private int maxFollowing;

    // ==================== CREATE ====================

    @Transactional
    @CacheEvict(value = {"followStats", "followingIds"}, allEntries = true)
    public FollowResponse follow(UUID followerId, FollowRequest request) {
        UUID followingId = request.getUserId();

        // Validate not self
        if (followerId.equals(followingId)) {
            throw new ValidationException("Cannot follow yourself");
        }

        // Check if blocked
        if (blockRepository.isBlockedEitherWay(followerId, followingId)) {
            throw new ValidationException("Cannot follow this user");
        }

        // Check if already following
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new ValidationException("Already following this user");
        }

        // Check max following limit
        long followingCount = followRepository.countByFollowerId(followerId);
        if (followingCount >= maxFollowing) {
            throw new ValidationException("Maximum following limit reached");
        }

        Follow follow = Follow.builder()
                .followerId(followerId)
                .followingId(followingId)
                .notificationsEnabled(request.getNotificationsEnabled() != null ? request.getNotificationsEnabled() : true)
                .isCloseFriend(request.getIsCloseFriend() != null ? request.getIsCloseFriend() : false)
                .build();

        follow = followRepository.save(follow);

        // Publish event
        eventPublisher.publishFollowed(follow);

        log.info("User {} followed {}", followerId, followingId);
        return mapToFollowResponse(follow);
    }

    // ==================== DELETE ====================

    @Transactional
    @CacheEvict(value = {"followStats", "followingIds"}, allEntries = true)
    public void unfollow(UUID followerId, UUID followingId) {
        if (!followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new ResourceNotFoundException("Follow relationship not found");
        }

        // Use hard delete
        followRepository.hardDelete(followerId, followingId);

        // Publish event
        eventPublisher.publishUnfollowed(followerId, followingId);

        log.info("User {} unfollowed {}", followerId, followingId);
    }

    // ==================== UPDATE ====================

    @Transactional
    @CacheEvict(value = {"followStats"}, allEntries = true)
    public FollowResponse updateFollow(UUID followerId, UUID followingId, UpdateFollowRequest request) {
        Follow follow = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .orElseThrow(() -> new ResourceNotFoundException("Follow relationship not found"));

        if (request.getNotificationsEnabled() != null) {
            follow.setNotificationsEnabled(request.getNotificationsEnabled());
        }
        if (request.getIsCloseFriend() != null) {
            follow.setIsCloseFriend(request.getIsCloseFriend());
        }
        if (request.getIsMuted() != null) {
            follow.setIsMuted(request.getIsMuted());
        }

        follow = followRepository.save(follow);

        log.info("User {} updated follow settings for {}", followerId, followingId);
        return mapToFollowResponse(follow);
    }

    // ==================== READ ====================

    @Transactional(readOnly = true)
    public PageResponse<FollowResponse> getFollowers(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Follow> followPage = followRepository.findByFollowingId(userId, pageRequest);
        return PageResponse.from(followPage, this::mapToFollowerResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<FollowResponse> getFollowing(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Follow> followPage = followRepository.findByFollowerId(userId, pageRequest);
        return PageResponse.from(followPage, this::mapToFollowResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<FollowResponse> getCloseFriends(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Follow> followPage = followRepository.findByFollowerIdAndIsCloseFriendTrue(userId, pageRequest);
        return PageResponse.from(followPage, this::mapToFollowResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "followStats", key = "#userId")
    public FollowStatsResponse getFollowStats(UUID userId, UUID requesterId) {
        long followersCount = followRepository.countByFollowingId(userId);
        long followingCount = followRepository.countByFollowerId(userId);

        boolean isFollowing = false;
        boolean isFollowedBy = false;

        if (requesterId != null && !requesterId.equals(userId)) {
            isFollowing = followRepository.existsByFollowerIdAndFollowingId(requesterId, userId);
            isFollowedBy = followRepository.existsByFollowerIdAndFollowingId(userId, requesterId);
        }

        return FollowStatsResponse.builder()
                .userId(userId)
                .followersCount(followersCount)
                .followingCount(followingCount)
                .isFollowing(isFollowing)
                .isFollowedBy(isFollowedBy)
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "followingIds", key = "#userId")
    public List<UUID> getFollowingIds(UUID userId) {
        return followRepository.findFollowingIdsByFollowerId(userId);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(UUID followerId, UUID followingId) {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    // ==================== MAPPERS ====================

    /**
     * Maps Follow entity to FollowResponse with user details of the FOLLOWING user
     * Used for: getFollowing, getCloseFriends
     */
    private FollowResponse mapToFollowResponse(Follow follow) {
        UserSummary userSummary = fetchUserSummary(follow.getFollowingId());

        return FollowResponse.builder()
                .id(follow.getId())
                .followerId(follow.getFollowerId())
                .followingId(follow.getFollowingId())
                .notificationsEnabled(follow.getNotificationsEnabled())
                .isCloseFriend(follow.getIsCloseFriend())
                .isMuted(follow.getIsMuted())
                .createdAt(follow.getCreatedAt())
                .user(userSummary)
                .build();
    }

    /**
     * Maps Follow entity to FollowResponse with user details of the FOLLOWER user
     * Used for: getFollowers
     */
    private FollowResponse mapToFollowerResponse(Follow follow) {
        UserSummary userSummary = fetchUserSummary(follow.getFollowerId());

        return FollowResponse.builder()
                .id(follow.getId())
                .followerId(follow.getFollowerId())
                .followingId(follow.getFollowingId())
                .notificationsEnabled(follow.getNotificationsEnabled())
                .isCloseFriend(follow.getIsCloseFriend())
                .isMuted(follow.getIsMuted())
                .createdAt(follow.getCreatedAt())
                .user(userSummary)
                .build();
    }

    /**
     * Fetches user summary from identity-service with fallback
     */
    private UserSummary fetchUserSummary(UUID userId) {
        try {
            return userServiceClient.getUserSummary(userId);
        } catch (Exception e) {
            log.warn("Failed to fetch user summary for {}: {}", userId, e.getMessage());
            return UserSummary.builder()
                    .id(userId)
                    .username("Unknown")
                    .email("unknown@gmail.com")
                    .build();
        }
    }
}