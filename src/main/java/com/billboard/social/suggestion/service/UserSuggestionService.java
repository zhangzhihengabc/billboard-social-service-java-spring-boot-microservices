package com.billboard.social.suggestion.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.FollowRepository;
import com.billboard.social.graph.repository.FriendshipRepository;
import com.billboard.social.suggestion.dto.response.SuggestionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSuggestionService {

    private final FriendshipRepository friendshipRepository;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final UserServiceClient userServiceClient;

    /** Max suggestions to return per request. */
    private static final int MAX_SUGGESTIONS = 20;

    /** If friends-of-friends returns fewer than this, add popular users. */
    private static final int MIN_GRAPH_SUGGESTIONS = 5;

    // ========================== PUBLIC API ==========================

    /**
     * Get suggested users for the given user.
     */
    public List<SuggestionResponse> getSuggestions(Long userId) {

        Set<Long> excluded = buildExclusionSet(userId);

        List<SuggestionResponse> suggestions = findFriendsOfFriends(userId, excluded);

        if (suggestions.size() < MIN_GRAPH_SUGGESTIONS) {

            suggestions.forEach(s -> excluded.add(s.getSuggestedUserId()));

            int needed = MAX_SUGGESTIONS - suggestions.size();
            List<SuggestionResponse> popular = findPopularUsers(userId, excluded, needed);
            suggestions.addAll(popular);
        }

        log.debug("Generated {} suggestions for user {}", suggestions.size(), userId);
        return suggestions;
    }

    // ========================== SUGGESTION ALGORITHMS ==========================

    /**
     * Find friends-of-friends for the given user.
     */
    private List<SuggestionResponse> findFriendsOfFriends(Long userId, Set<Long> excluded) {

        // Get my friend IDs
        List<Long> myFriendIds = friendshipRepository.findFriendIds(userId);

        if (myFriendIds.isEmpty()) {
            log.debug("User {} has no friends — skipping friends-of-friends", userId);
            return new ArrayList<>();
        }

        Map<Long, Integer> candidateMutualCount = new HashMap<>();

        for (Long friendId : myFriendIds) {
            List<Long> theirFriends = friendshipRepository.findFriendIds(friendId);

            for (Long candidate : theirFriends) {
                // Skip anyone in the exclusion set
                if (!excluded.contains(candidate)) {
                    candidateMutualCount.merge(candidate, 1, Integer::sum);
                }
            }
        }

        // Sort by mutual count descending, take top N, convert to response DTOs
        return candidateMutualCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(MAX_SUGGESTIONS)
                .map(entry -> {
                    int mutualCount = entry.getValue();
                    String reason = mutualCount + (mutualCount == 1 ? " mutual friend" : " mutual friends");

                    return SuggestionResponse.builder()
                            .suggestedUserId(entry.getKey())
                            .mutualFriendCount(mutualCount)
                            .reason(reason)
                            .source("FRIEND_OF_FRIEND")
                            .user(fetchUserSummary(entry.getKey()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Fallback: find the most-followed users on the platform.
     */
    private List<SuggestionResponse> findPopularUsers(Long userId, Set<Long> excluded, int limit) {

        // The native query requires a non-empty exclusion list.
        // At minimum, excluded contains the userId itself.
        List<Long> excludedList = excluded.isEmpty()
                ? List.of(userId)
                : new ArrayList<>(excluded);

        List<Object[]> rows = followRepository.findMostFollowedUserIds(excludedList, limit);

        if (rows.isEmpty()) {
            log.debug("No popular users found (platform may be new)");
            return new ArrayList<>();
        }

        return rows.stream()
                .map(row -> {
                    Long candidateId = ((Number) row[0]).longValue();
                    long followerCount = ((Number) row[1]).longValue();

                    String reason = followerCount >= 100
                            ? "Popular in your community"
                            : followerCount + " people follow this user";

                    return SuggestionResponse.builder()
                            .suggestedUserId(candidateId)
                            .mutualFriendCount(0)
                            .reason(reason)
                            .source("POPULAR")
                            .user(fetchUserSummary(candidateId))
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ========================== HELPERS ==========================

    /**
     * Build the set of user IDs that must never appear in suggestions.
     */
    private Set<Long> buildExclusionSet(Long userId) {
        Set<Long> excluded = new HashSet<>();

        excluded.add(userId);
        excluded.addAll(friendshipRepository.findFriendIds(userId));
        excluded.addAll(blockRepository.findBlockedUserIds(userId));
        excluded.addAll(blockRepository.findBlockedByUserIds(userId));

        return excluded;
    }

    /**
     * Fetch user summary (username, avatar, email) from the SSO service via Feign.
     * Returns null if the SSO service is unavailable — the suggestion still works,
     * the frontend just won't have the username/avatar until next call.
     */
    private UserSummary fetchUserSummary(Long userId) {
        try {
            return userServiceClient.getUserSummary(userId);
        } catch (Exception e) {
            log.warn("Could not fetch user summary for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }
}