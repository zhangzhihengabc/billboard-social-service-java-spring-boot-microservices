package com.billboard.social.graph.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Friendship;
import com.billboard.social.graph.entity.FriendshipEvent;
import com.billboard.social.graph.entity.enums.FriendshipStatus;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.FriendshipEventRepository;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final FriendshipEventRepository friendshipEventRepository;
    private final BlockRepository blockRepository;
    private final UserServiceClient userServiceClient;
    private final SocialEventPublisher eventPublisher;

    @Value("${app.friendship.max-friends:5000}")
    private int maxFriends;

    @Transactional
    public FriendshipResponse sendFriendRequest(Long requesterId, FriendRequest request) {
        if (request.getUserId() == null) {
            throw new ValidationException("User ID is required");
        }

        Long addresseeId = request.getUserId();

        if (requesterId.equals(addresseeId)) {
            throw new ValidationException("Cannot send friend request to yourself");
        }

        validateUserExists(addresseeId);

        if (blockRepository.isBlockedEitherWay(requesterId, addresseeId)) {
            throw new ValidationException("Cannot send friend request to this user");
        }

        Optional<Friendship> existingOpt = friendshipRepository.findBetweenUsers(requesterId, addresseeId);
        if (existingOpt.isPresent()) {
            Friendship existing = existingOpt.get();
            switch (existing.getStatus()) {
                case ACCEPTED:
                    throw new ValidationException("Already friends with this user");
                case PENDING:
                    throw new ValidationException("Friend request already pending");
                case BLOCKED:
                    // indistinguishable from the isBlockedEitherWay refusal above
                    throw new ValidationException("Cannot send friend request to this user");
                default:
                    // DECLINED, CANCELLED, UNFRIENDED — reactivate
                    FriendshipStatus previous = existing.getStatus();
                    // Flip direction so the new requester appears correctly in pending-inbox queries.
                    // findPendingRequests filters by addresseeId = currentUser; without a flip the
                    // wrong user would receive the pending notification.
                    existing.setRequesterId(requesterId);
                    existing.setAddresseeId(addresseeId);
                    existing.setAcceptedAt(null);
                    existing.setStatus(FriendshipStatus.PENDING);
                    existing.setMessage(request.getMessage());
                    List<Long> newMutuals = friendshipRepository.findMutualFriendIds(requesterId, addresseeId);
                    existing.setMutualFriendsCount(newMutuals.size());
                    existing = friendshipRepository.save(existing);
                    recordEvent(existing, previous, FriendshipStatus.PENDING, requesterId);
                    eventPublisher.publishFriendRequestSent(existing);
                    log.info("Friend request reactivated from {} to {}", requesterId, addresseeId);
                    return mapToFriendshipResponse(existing);
            }
        }

        long friendCount = friendshipRepository.countFriends(requesterId);
        if (friendCount >= maxFriends) {
            throw new ValidationException("Maximum friends limit reached");
        }

        List<Long> mutualFriendIds = friendshipRepository.findMutualFriendIds(requesterId, addresseeId);

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

        recordEvent(friendship, null, FriendshipStatus.PENDING, requesterId);
        eventPublisher.publishFriendRequestSent(friendship);

        log.info("Friend request sent from {} to {}", requesterId, addresseeId);
        return mapToFriendshipResponse(friendship);
    }

    @Transactional
    @CacheEvict(value = {"friends", "friendIds"}, allEntries = true)
    public FriendshipResponse acceptFriendRequest(Long userId, UUID friendshipId) {
        Friendship friendship = findFriendshipOrThrow(friendshipId);

        if (!friendship.getAddresseeId().equals(userId)) {
            throw new ValidationException("Only the addressee can accept this request");
        }

        if (!friendship.isPending()) {
            throw new ValidationException("Friend request is not pending");
        }

        friendship.accept();
        friendship = friendshipRepository.save(friendship);

        recordEvent(friendship, FriendshipStatus.PENDING, FriendshipStatus.ACCEPTED, userId);
        eventPublisher.publishFriendRequestAccepted(friendship);

        log.info("Friend request {} accepted by {}", friendshipId, userId);
        return mapToFriendshipResponse(friendship);
    }

    @Transactional
    @CacheEvict(value = {"friends", "friendIds"}, allEntries = true)
    public void declineFriendRequest(Long userId, UUID friendshipId) {
        Friendship friendship = findFriendshipOrThrow(friendshipId);

        if (!friendship.getAddresseeId().equals(userId)) {
            throw new ValidationException("Only the addressee can decline this request");
        }

        if (!friendship.isPending()) {
            throw new ValidationException("Friend request is not pending");
        }

        friendship.decline();
        friendship = friendshipRepository.save(friendship);
        recordEvent(friendship, FriendshipStatus.PENDING, FriendshipStatus.DECLINED, userId);
        eventPublisher.publishFriendRequestDeclined(friendship);

        log.info("Friend request {} declined by {}", friendshipId, userId);
    }

    @Transactional
    public void cancelFriendRequest(Long userId, UUID friendshipId) {
        Friendship friendship = findFriendshipOrThrow(friendshipId);

        if (!friendship.getRequesterId().equals(userId)) {
            throw new ValidationException("Only the requester can cancel this request");
        }

        if (!friendship.isPending()) {
            throw new ValidationException("Friend request is not pending");
        }

        friendship.cancel();
        friendship = friendshipRepository.save(friendship);
        recordEvent(friendship, FriendshipStatus.PENDING, FriendshipStatus.CANCELLED, userId);

        log.info("Friend request {} cancelled by {}", friendshipId, userId);
    }

    @Transactional
    @CacheEvict(value = {"friends", "friendIds"}, allEntries = true)
    public void unfriend(Long userId, Long friendId) {
        Friendship friendship = friendshipRepository.findBetweenUsers(userId, friendId)
                .orElseThrow(() -> new ValidationException("Friendship not found"));

        if (!friendship.isAccepted()) {
            throw new ValidationException("Not friends with this user");
        }

        friendship.unfriend();
        friendship = friendshipRepository.save(friendship);
        recordEvent(friendship, FriendshipStatus.ACCEPTED, FriendshipStatus.UNFRIENDED, userId);

        eventPublisher.publishUnfriended(userId, friendId);

        log.info("User {} unfriended {}", userId, friendId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "friends", key = "#userId + '_' + #page + '_' + #size")
    public PageResponse<FriendResponse> getFriends(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "acceptedAt"));
        log.info("Fetching friends for user {} - page: {}, size: {}", userId, page, size);
        Page<Friendship> friendships = friendshipRepository.findAcceptedFriendships(userId, pageRequest);
        log.info("Found {} friends for user {} on page {}", friendships.getTotalElements(), userId, page);
        return PageResponse.from(friendships, f -> mapToFriendResponse(f, userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<FriendshipResponse> getPendingRequests(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Friendship> requests = friendshipRepository.findPendingRequests(userId, pageRequest);
        return PageResponse.from(requests, this::mapToFriendshipResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<FriendshipResponse> getSentRequests(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Friendship> requests = friendshipRepository.findSentRequests(userId, pageRequest);
        return PageResponse.from(requests, this::mapToFriendshipResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "friendIds", key = "#userId")
    public List<Long> getFriendIds(Long userId) {
        return friendshipRepository.findFriendIds(userId);
    }

    @Transactional(readOnly = true)
    public List<Long> getMutualFriendIds(Long userId1, Long userId2) {
        return friendshipRepository.findMutualFriendIds(userId1, userId2);
    }

    @Transactional(readOnly = true)
    public boolean areFriends(Long userId1, Long userId2) {
        return friendshipRepository.areFriends(userId1, userId2);
    }

    @Transactional(readOnly = true)
    public long getFriendsCount(Long userId) {
        return friendshipRepository.countFriends(userId);
    }

    private void recordEvent(Friendship f, FriendshipStatus from, FriendshipStatus to, Long actor) {
        friendshipEventRepository.save(FriendshipEvent.builder()
                .friendshipId(f.getId())
                .requesterId(f.getRequesterId())
                .addresseeId(f.getAddresseeId())
                .fromStatus(from)
                .toStatus(to)
                .actorUserId(actor)
                .build());
    }

    private Friendship findFriendshipOrThrow(UUID friendshipId) {
        return friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ValidationException("Friendship not found with id: " + friendshipId));
    }

    private void validateUserExists(Long userId) {
        try {
            UserSummary user = userServiceClient.getUserSummary(userId).getData();
            if (user == null) {
                log.debug("User not found in identity-service (null response): userId={}", userId);
                throw new ValidationException("Unable to send friend request");
            }
        } catch (FeignException.NotFound e) {
            log.warn("User not found in identity-service: {}", userId);
            throw new ValidationException("Unable to send friend request");
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
        int mutualFriendsCount = friendshipRepository
                .findMutualFriendIds(friendship.getRequesterId(), friendship.getAddresseeId())
                .size();

        return FriendshipResponse.builder()
                .id(friendship.getId())
                .requesterId(friendship.getRequesterId())
                .addresseeId(friendship.getAddresseeId())
                .status(friendship.getStatus())
                .message(friendship.getMessage())
                .mutualFriendsCount(mutualFriendsCount)
                .acceptedAt(friendship.getAcceptedAt())
                .createdAt(friendship.getCreatedAt())
                .build();
    }

    private FriendResponse mapToFriendResponse(Friendship friendship, Long userId) {
        Long friendId = friendship.getRequesterId().equals(userId)
                ? friendship.getAddresseeId()
                : friendship.getRequesterId();

        UserSummary userSummary = userServiceClient.getUserSummary(friendId).getData();
        int mutualFriendsCount = friendshipRepository.findMutualFriendIds(userId, friendId).size();

        return FriendResponse.builder()
                .friendId(friendId)
                .username(userSummary != null ? userSummary.getUsername() : null)
                .mutualFriendsCount(mutualFriendsCount)
                .friendsSince(friendship.getAcceptedAt())
                .build();
    }
}
