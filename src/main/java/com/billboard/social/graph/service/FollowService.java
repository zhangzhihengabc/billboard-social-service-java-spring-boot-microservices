package com.billboard.social.graph.service;
import com.billboard.social.common.dto.UserSummary;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Follow;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowService {

    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final UserServiceClient userServiceClient;
    private final SocialEventPublisher eventPublisher;

    @Value("${app.following.max-following:10000}")
    private int maxFollowing;

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

    @Transactional
    @CacheEvict(value = {"followStats", "followingIds"}, allEntries = true)
    public void unfollow(UUID followerId, UUID followingId) {
        Follow follow = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
            .orElseThrow(() -> new ResourceNotFoundException("Follow relationship not found"));

        followRepository.delete(follow);

        // Publish event
        eventPublisher.publishUnfollowed(followerId, followingId);

        log.info("User {} unfollowed {}", followerId, followingId);
    }

    @Transactional
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

        log.info("Follow relationship updated for {} -> {}", followerId, followingId);
        return mapToFollowResponse(follow);
    }

    @Transactional(readOnly = true)
    public Page<FollowResponse> getFollowers(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Follow> followers = followRepository.findByFollowingId(userId, pageRequest);
        return followers.map(this::mapToFollowResponse);
    }

    @Transactional(readOnly = true)
    public Page<FollowResponse> getFollowing(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Follow> following = followRepository.findByFollowerId(userId, pageRequest);
        return following.map(this::mapToFollowResponse);
    }

    @Transactional(readOnly = true)
    public Page<FollowResponse> getCloseFriends(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Follow> closeFriends = followRepository.findCloseFriends(userId, pageRequest);
        return closeFriends.map(this::mapToFollowResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "followingIds", key = "#userId")
    public List<UUID> getFollowingIds(UUID userId) {
        return followRepository.findFollowingIds(userId);
    }

    @Transactional(readOnly = true)
    public List<UUID> getFollowerIds(UUID userId) {
        return followRepository.findFollowerIds(userId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "followStats", key = "#userId")
    public FollowStatsResponse getFollowStats(UUID userId, UUID currentUserId) {
        long followersCount = followRepository.countByFollowingId(userId);
        long followingCount = followRepository.countByFollowerId(userId);

        boolean isFollowing = false;
        boolean isFollowedBy = false;

        if (currentUserId != null && !currentUserId.equals(userId)) {
            isFollowing = followRepository.isFollowing(currentUserId, userId);
            isFollowedBy = followRepository.isFollowing(userId, currentUserId);
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
    public boolean isFollowing(UUID followerId, UUID followingId) {
        return followRepository.isFollowing(followerId, followingId);
    }

    private FollowResponse mapToFollowResponse(Follow follow) {
        UserSummary userSummary = userServiceClient.getUserSummary(follow.getFollowingId());

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
}
