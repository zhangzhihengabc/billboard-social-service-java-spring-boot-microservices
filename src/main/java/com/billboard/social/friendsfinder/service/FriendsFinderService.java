package com.billboard.social.friendsfinder.service;

import com.billboard.social.common.client.EsportsBackendClient;
import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.PlayerDto;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.friendsfinder.dto.FriendsFinderDtos.FriendFinderResultResponse;
import com.billboard.social.friendsfinder.dto.FriendsFinderDtos.FriendSuggestionResponse;
import com.billboard.social.friendsfinder.dto.FriendsFinderDtos.ScrimHistoryResponse;
import com.billboard.social.friendsfinder.entity.FriendSuggestion;
import com.billboard.social.friendsfinder.entity.ScrimHistory;
import com.billboard.social.friendsfinder.repository.FriendSuggestionRepository;
import com.billboard.social.friendsfinder.repository.ScrimHistoryRepository;
import com.billboard.social.graph.entity.Friendship;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendsFinderService {

    private final EsportsBackendClient esportsBackendClient;
    private final FriendshipRepository friendshipRepository;
    private final BlockRepository blockRepository;
    private final FriendSuggestionRepository friendSuggestionRepository;
    private final ScrimHistoryRepository scrimHistoryRepository;

    // ── Search players with friendship enrichment ─────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<FriendFinderResultResponse> searchPlayers(
            Long currentUserId, String region, Integer minSkill,
            Integer maxSkill, int page, int size) {

        List<PlayerDto> players;
        try {
            players = esportsBackendClient.searchPlayersByCriteria(
                    region, minSkill, maxSkill, page, size);
        } catch (Exception e) {
            log.warn("FriendsFinderService: Feign call to searchPlayersByCriteria failed: {}", e.getMessage());
            return PageResponse.empty(page, size);
        }

        if (players == null || players.isEmpty()) {
            return PageResponse.empty(page, size);
        }

        List<FriendFinderResultResponse> results = new ArrayList<>();

        for (PlayerDto player : players) {
            if (player == null || player.getId() == null) continue;

            // PlayerDto.getUser() is the SSO userId as String — parse to Long
            Long candidateUserId;
            try {
                candidateUserId = Long.valueOf(player.getUser());
            } catch (NumberFormatException e) {
                log.debug("FriendsFinderService: Skipping player with non-numeric userId: {}", player.getUser());
                continue;
            }

            // Skip self
            if (currentUserId.equals(candidateUserId)) continue;

            // Skip blocked users
            try {
                if (blockRepository.isBlockedEitherWay(currentUserId, candidateUserId)) continue;
            } catch (Exception e) {
                log.debug("FriendsFinderService: Block check failed for {}: {}", candidateUserId, e.getMessage());
            }

            // Determine friendship status
            String friendshipStatus = "NONE";
            try {
                Optional<Friendship> friendship = friendshipRepository.findBetweenUsers(currentUserId, candidateUserId);
                if (friendship.isPresent()) {
                    if (friendship.get().isAccepted()) {
                        friendshipStatus = "ACCEPTED";
                    } else if (friendship.get().isPending()) {
                        friendshipStatus = "PENDING";
                    }
                }
            } catch (Exception e) {
                log.debug("FriendsFinderService: Friendship check failed for {}: {}", candidateUserId, e.getMessage());
            }

            // Mutual friends count
            int mutualFriendCount = 0;
            try {
                mutualFriendCount = friendshipRepository.findMutualFriendIds(currentUserId, candidateUserId).size();
            } catch (Exception e) {
                log.debug("FriendsFinderService: Mutual friends check failed for {}: {}", candidateUserId, e.getMessage());
            }

            // Scrim count
            int scrimCount = 0;
            try {
                scrimCount = scrimHistoryRepository.countBetweenUsers(currentUserId, candidateUserId);
            } catch (Exception e) {
                log.debug("FriendsFinderService: Scrim count failed for {}: {}", candidateUserId, e.getMessage());
            }

            results.add(FriendFinderResultResponse.builder()
                    .userId(candidateUserId)
                    .gamerTag(player.getGamerTag() != null ? player.getGamerTag() : "Unknown")
                    .skillLevel(null)   // PlayerDto doesn't carry skillLevel — acceptable for v1
                    .region(region)
                    .avatarUrl(null)    // PlayerDto doesn't carry avatarUrl — acceptable for v1
                    .friendshipStatus(friendshipStatus)
                    .mutualFriendCount(mutualFriendCount)
                    .scrimCount(scrimCount)
                    .lastScrimAt(null)  // Could query scrim_history — deferred to v2
                    .build());
        }

        return PageResponse.<FriendFinderResultResponse>builder()
                .content(results)
                .page(page)
                .size(size)
                .totalElements(results.size())
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(results.isEmpty())
                .build();
    }

    // ── Get friend suggestions ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<FriendSuggestionResponse> getSuggestions(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<FriendSuggestion> suggestions = friendSuggestionRepository
                .findByUserIdAndDismissedFalseOrderBySuggestionScoreDesc(userId, pageRequest);

        return PageResponse.from(suggestions, s -> {
            String gamerTag = "Unknown";
            String avatarUrl = null;

            try {
                PlayerDto player = esportsBackendClient.getPlayerByUserId(String.valueOf(s.getSuggestedUserId()));
                if (player != null) {
                    gamerTag = player.getGamerTag() != null ? player.getGamerTag() : "Unknown";
                }
            } catch (Exception e) {
                log.debug("FriendsFinderService: Could not fetch player for suggestion {}: {}",
                        s.getSuggestedUserId(), e.getMessage());
            }

            return FriendSuggestionResponse.builder()
                    .id(s.getId())
                    .suggestedUserId(s.getSuggestedUserId())
                    .gamerTag(gamerTag)
                    .avatarUrl(avatarUrl)
                    .suggestionScore(s.getSuggestionScore())
                    .source(s.getSource())
                    .gameMode(s.getGameMode())
                    .interactionCount(s.getInteractionCount())
                    .mutualFriendCount(s.getMutualFriendCount())
                    .createdAt(s.getCreatedAt())
                    .build();
        });
    }

    // ── Dismiss a suggestion ──────────────────────────────────────────────

    @Transactional
    public void dismissSuggestion(Long userId, UUID suggestionId) {
        FriendSuggestion suggestion = friendSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("FriendSuggestion", "id", suggestionId));

        if (!suggestion.getUserId().equals(userId)) {
            throw new ValidationException("You can only dismiss your own suggestions");
        }

        suggestion.setDismissed(true);
        suggestion.setDismissedAt(LocalDateTime.now());
        friendSuggestionRepository.save(suggestion);

        log.info("FriendsFinderService: User {} dismissed suggestion {}", userId, suggestionId);
    }

    // ── Get scrim history ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<ScrimHistoryResponse> getScrimHistory(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "playedAt"));
        Page<ScrimHistory> history = scrimHistoryRepository.findByUserId(userId, pageRequest);

        return PageResponse.from(history, h -> {
            Long opponentUserId = h.getUserIdA().equals(userId) ? h.getUserIdB() : h.getUserIdA();

            String opponentGamerTag = "Unknown";
            try {
                PlayerDto opponent = esportsBackendClient.getPlayerByUserId(String.valueOf(opponentUserId));
                if (opponent != null && opponent.getGamerTag() != null) {
                    opponentGamerTag = opponent.getGamerTag();
                }
            } catch (Exception e) {
                log.debug("FriendsFinderService: Could not fetch opponent player {}: {}",
                        opponentUserId, e.getMessage());
            }

            String friendshipStatus = "NONE";
            try {
                Optional<Friendship> friendship = friendshipRepository.findBetweenUsers(userId, opponentUserId);
                if (friendship.isPresent()) {
                    if (friendship.get().isAccepted()) friendshipStatus = "ACCEPTED";
                    else if (friendship.get().isPending()) friendshipStatus = "PENDING";
                }
            } catch (Exception e) {
                log.debug("FriendsFinderService: Friendship check failed for {}: {}", opponentUserId, e.getMessage());
            }

            return ScrimHistoryResponse.builder()
                    .id(h.getId())
                    .opponentUserId(opponentUserId)
                    .opponentGamerTag(opponentGamerTag)
                    .gameMode(h.getGameMode())
                    .matchQualityScore(h.getMatchQualityScore())
                    .playedAt(h.getPlayedAt())
                    .friendshipStatus(friendshipStatus)
                    .build();
        });
    }
}
