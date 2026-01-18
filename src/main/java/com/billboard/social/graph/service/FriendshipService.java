package com.billboard.social.graph.service;
import com.billboard.social.common.dto.UserSummary;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Friendship;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.repository.BlockRepository;
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
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final BlockRepository blockRepository;
    private final UserServiceClient userServiceClient;
    private final SocialEventPublisher eventPublisher;

    @Value("${app.friendship.max-friends:5000}")
    private int maxFriends;

    @Transactional
    public FriendshipResponse sendFriendRequest(UUID requesterId, FriendRequest request) {
        UUID addresseeId = request.getUserId();

        // Validate not self
        if (requesterId.equals(addresseeId)) {
            throw new ValidationException("Cannot send friend request to yourself");
        }

        // Check if blocked
        if (blockRepository.isBlockedEitherWay(requesterId, addresseeId)) {
            throw new ValidationException("Cannot send friend request to this user");
        }

        // Check existing friendship
        friendshipRepository.findBetweenUsers(requesterId, addresseeId)
            .ifPresent(f -> {
                if (f.isAccepted()) {
                    throw new ValidationException("Already friends with this user");
                }
                if (f.isPending()) {
                    throw new ValidationException("Friend request already pending");
                }
            });

        // Check max friends limit
        long friendCount = friendshipRepository.countFriends(requesterId);
        if (friendCount >= maxFriends) {
            throw new ValidationException("Maximum friends limit reached");
        }

        // Calculate mutual friends
        List<UUID> mutualFriendIds = friendshipRepository.findMutualFriendIds(requesterId, addresseeId);

        Friendship friendship = Friendship.builder()
            .requesterId(requesterId)
            .addresseeId(addresseeId)
            .message(request.getMessage())
            .mutualFriendsCount(mutualFriendIds.size())
            .build();

        friendship = friendshipRepository.save(friendship);

        // Publish event
        eventPublisher.publishFriendRequestSent(friendship);

        log.info("Friend request sent from {} to {}", requesterId, addresseeId);
        return mapToFriendshipResponse(friendship);
    }

    @Transactional
    @CacheEvict(value = {"friends", "friendIds"}, allEntries = true)
    public FriendshipResponse acceptFriendRequest(UUID userId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
            .orElseThrow(() -> new ResourceNotFoundException("Friendship", "id", friendshipId));

        if (!friendship.getAddresseeId().equals(userId)) {
            throw new ValidationException("Only the addressee can accept this request");
        }

        if (!friendship.isPending()) {
            throw new ValidationException("Friend request is not pending");
        }

        friendship.accept();
        friendship = friendshipRepository.save(friendship);

        // Publish event
        eventPublisher.publishFriendRequestAccepted(friendship);

        log.info("Friend request {} accepted by {}", friendshipId, userId);
        return mapToFriendshipResponse(friendship);
    }

    @Transactional
    public FriendshipResponse declineFriendRequest(UUID userId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
            .orElseThrow(() -> new ResourceNotFoundException("Friendship", "id", friendshipId));

        if (!friendship.getAddresseeId().equals(userId)) {
            throw new ValidationException("Only the addressee can decline this request");
        }

        if (!friendship.isPending()) {
            throw new ValidationException("Friend request is not pending");
        }

        friendship.decline();
        friendship = friendshipRepository.save(friendship);

        log.info("Friend request {} declined by {}", friendshipId, userId);
        return mapToFriendshipResponse(friendship);
    }

    @Transactional
    public void cancelFriendRequest(UUID userId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
            .orElseThrow(() -> new ResourceNotFoundException("Friendship", "id", friendshipId));

        if (!friendship.getRequesterId().equals(userId)) {
            throw new ValidationException("Only the requester can cancel this request");
        }

        if (!friendship.isPending()) {
            throw new ValidationException("Friend request is not pending");
        }

        friendship.cancel();
        friendshipRepository.save(friendship);

        log.info("Friend request {} cancelled by {}", friendshipId, userId);
    }

    @Transactional
    @CacheEvict(value = {"friends", "friendIds"}, allEntries = true)
    public void unfriend(UUID userId, UUID friendId) {
        Friendship friendship = friendshipRepository.findBetweenUsers(userId, friendId)
            .orElseThrow(() -> new ResourceNotFoundException("Friendship not found"));

        if (!friendship.isAccepted()) {
            throw new ValidationException("Not friends with this user");
        }

        friendship.softDelete();
        friendshipRepository.save(friendship);

        // Publish event
        eventPublisher.publishUnfriended(userId, friendId);

        log.info("User {} unfriended {}", userId, friendId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "friends", key = "#userId + '_' + #page + '_' + #size")
    public Page<FriendResponse> getFriends(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "acceptedAt"));
        Page<Friendship> friendships = friendshipRepository.findAcceptedFriendships(userId, pageRequest);

        return friendships.map(f -> {
            UUID friendId = f.getRequesterId().equals(userId) ? f.getAddresseeId() : f.getRequesterId();
            UserSummary userSummary = userServiceClient.getUserSummary(friendId);
            
            return FriendResponse.builder()
                .friendId(friendId)
                .username(userSummary != null ? userSummary.getUsername() : null)
                .displayName(userSummary != null ? userSummary.getDisplayName() : null)
                .avatarUrl(userSummary != null ? userSummary.getAvatarUrl() : null)
                .isVerified(userSummary != null ? userSummary.getIsVerified() : false)
                .mutualFriendsCount(f.getMutualFriendsCount())
                .friendsSince(f.getAcceptedAt())
                .build();
        });
    }

    @Transactional(readOnly = true)
    public Page<FriendshipResponse> getPendingRequests(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Friendship> requests = friendshipRepository.findPendingRequests(userId, pageRequest);
        return requests.map(this::mapToFriendshipResponse);
    }

    @Transactional(readOnly = true)
    public Page<FriendshipResponse> getSentRequests(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Friendship> requests = friendshipRepository.findSentRequests(userId, pageRequest);
        return requests.map(this::mapToFriendshipResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "friendIds", key = "#userId")
    public List<UUID> getFriendIds(UUID userId) {
        return friendshipRepository.findFriendIds(userId);
    }

    @Transactional(readOnly = true)
    public List<UUID> getMutualFriendIds(UUID userId1, UUID userId2) {
        return friendshipRepository.findMutualFriendIds(userId1, userId2);
    }

    @Transactional(readOnly = true)
    public boolean areFriends(UUID userId1, UUID userId2) {
        return friendshipRepository.areFriends(userId1, userId2);
    }

    @Transactional(readOnly = true)
    public long getFriendsCount(UUID userId) {
        return friendshipRepository.countFriends(userId);
    }

    private FriendshipResponse mapToFriendshipResponse(Friendship friendship) {
        return FriendshipResponse.builder()
            .id(friendship.getId())
            .requesterId(friendship.getRequesterId())
            .addresseeId(friendship.getAddresseeId())
            .status(friendship.getStatus())
            .message(friendship.getMessage())
            .mutualFriendsCount(friendship.getMutualFriendsCount())
            .acceptedAt(friendship.getAcceptedAt())
            .createdAt(friendship.getCreatedAt())
            .build();
    }
}
