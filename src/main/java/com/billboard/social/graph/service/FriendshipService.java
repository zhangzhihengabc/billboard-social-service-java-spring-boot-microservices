package com.billboard.social.graph.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Friendship;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.repository.BlockRepository;
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
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final BlockRepository blockRepository;
    private final UserServiceClient userServiceClient;
    private final SocialEventPublisher eventPublisher;

    @Value("${app.friendship.max-friends:5000}")
    private int maxFriends;

    @Transactional
    public FriendshipResponse sendFriendRequest(UUID requesterId, FriendRequest request) {
        if (request.getUserId() == null) {
            throw new ValidationException("User ID is required");
        }

        UUID addresseeId = request.getUserId();

        if (requesterId.equals(addresseeId)) {
            throw new ValidationException("Cannot send friend request to yourself");
        }

        validateUserExists(addresseeId);

        if (blockRepository.isBlockedEitherWay(requesterId, addresseeId)) {
            throw new ValidationException("Cannot send friend request to this user");
        }

        friendshipRepository.findBetweenUsers(requesterId, addresseeId)
                .ifPresent(f -> {
                    if (f.isAccepted()) {
                        throw new ValidationException("Already friends with this user");
                    }
                    if (f.isPending()) {
                        throw new ValidationException("Friend request already pending");
                    }
                });

        long friendCount = friendshipRepository.countFriends(requesterId);
        if (friendCount >= maxFriends) {
            throw new ValidationException("Maximum friends limit reached");
        }

        List<UUID> mutualFriendIds = friendshipRepository.findMutualFriendIds(requesterId, addresseeId);

        Friendship friendship = Friendship.builder()
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .message(request.getMessage())
                .mutualFriendsCount(mutualFriendIds.size())
                .build();

        try {
            friendship = friendshipRepository.save(friendship);
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition detected for friend request from {} to {}: {}",
                    requesterId, addresseeId, e.getMessage());
            throw new ValidationException("Friend request already exists or is pending");
        }

        eventPublisher.publishFriendRequestSent(friendship);

        log.info("Friend request sent from {} to {}", requesterId, addresseeId);
        return mapToFriendshipResponse(friendship);
    }

    @Transactional
    @CacheEvict(value = {"friends", "friendIds"}, allEntries = true)
    public FriendshipResponse acceptFriendRequest(UUID userId, UUID friendshipId) {
        Friendship friendship = findFriendshipOrThrow(friendshipId);

        if (!friendship.getAddresseeId().equals(userId)) {
            throw new ValidationException("Only the addressee can accept this request");
        }

        if (!friendship.isPending()) {
            throw new ValidationException("Friend request is not pending");
        }

        friendship.accept();
        friendship = friendshipRepository.save(friendship);

        eventPublisher.publishFriendRequestAccepted(friendship);

        log.info("Friend request {} accepted by {}", friendshipId, userId);
        return mapToFriendshipResponse(friendship);
    }

    @Transactional
    @CacheEvict(value = {"friends", "friendIds"}, allEntries = true)
    public void declineFriendRequest(UUID userId, UUID friendshipId) {
        Friendship friendship = findFriendshipOrThrow(friendshipId);

        if (!friendship.getAddresseeId().equals(userId)) {
            throw new ValidationException("Only the addressee can decline this request");
        }

        if (!friendship.isPending()) {
            throw new ValidationException("Friend request is not pending");
        }

        friendshipRepository.delete(friendship);

        log.info("Friend request {} declined by {}", friendshipId, userId);
    }

    @Transactional
    public void cancelFriendRequest(UUID userId, UUID friendshipId) {
        Friendship friendship = findFriendshipOrThrow(friendshipId);

        if (!friendship.getRequesterId().equals(userId)) {
            throw new ValidationException("Only the requester can cancel this request");
        }

        if (!friendship.isPending()) {
            throw new ValidationException("Friend request is not pending");
        }

        friendshipRepository.delete(friendship);

        log.info("Friend request {} cancelled by {}", friendshipId, userId);
    }

    @Transactional
    @CacheEvict(value = {"friends", "friendIds"}, allEntries = true)
    public void unfriend(UUID userId, UUID friendId) {
        Friendship friendship = friendshipRepository.findBetweenUsers(userId, friendId)
                .orElseThrow(() -> new ValidationException("Friendship not found"));

        if (!friendship.isAccepted()) {
            throw new ValidationException("Not friends with this user");
        }

        friendshipRepository.delete(friendship);

        eventPublisher.publishUnfriended(userId, friendId);

        log.info("User {} unfriended {}", userId, friendId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "friends", key = "#userId + '_' + #page + '_' + #size")
    public PageResponse<FriendResponse> getFriends(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "acceptedAt"));
        log.info("Fetching friends for user {} - page: {}, size: {}", userId, page, size);
        Page<Friendship> friendships = friendshipRepository.findAcceptedFriendships(userId, pageRequest);
        log.info("Found {} friends for user {} on page {}", friendships.getTotalElements(), userId, page);
        return PageResponse.from(friendships, f -> mapToFriendResponse(f, userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<FriendshipResponse> getPendingRequests(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Friendship> requests = friendshipRepository.findPendingRequests(userId, pageRequest);
        return PageResponse.from(requests, this::mapToFriendshipResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<FriendshipResponse> getSentRequests(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Friendship> requests = friendshipRepository.findSentRequests(userId, pageRequest);
        return PageResponse.from(requests, this::mapToFriendshipResponse);
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

    private Friendship findFriendshipOrThrow(UUID friendshipId) {
        return friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ValidationException("Friendship not found with id: " + friendshipId));
    }

    private void validateUserExists(UUID userId) {
        try {
            UserSummary user = userServiceClient.getUserSummary(userId);
            if (user == null) {
                throw new ValidationException("User not found with id: " + userId);
            }
        } catch (FeignException.NotFound e) {
            log.warn("User not found in identity-service: {}", userId);
            throw new ValidationException("User not found with id: " + userId);
        } catch (FeignException e) {
            log.error("Identity service error for userId {}: {} - Status: {}",
                    userId, e.getMessage(), e.status());
            throw new ValidationException("Unable to verify user. Please try again later.");
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error validating user {}: {}", userId, e.getMessage());
            throw new ValidationException("Unable to verify user. Please try again later.");
        }
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

    private FriendResponse mapToFriendResponse(Friendship friendship, UUID userId) {
        UUID friendId = friendship.getRequesterId().equals(userId)
                ? friendship.getAddresseeId()
                : friendship.getRequesterId();

        UserSummary userSummary = fetchUserSummaryWithFallback(friendId);

        return FriendResponse.builder()
                .friendId(friendId)
                .username(userSummary.getUsername())
                .mutualFriendsCount(friendship.getMutualFriendsCount())
                .friendsSince(friendship.getAcceptedAt())
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