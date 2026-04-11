package com.billboard.social.friendsfinder.service;

import com.billboard.social.friendsfinder.entity.FriendSuggestion;
import com.billboard.social.friendsfinder.entity.ScrimHistory;
import com.billboard.social.friendsfinder.repository.FriendSuggestionRepository;
import com.billboard.social.friendsfinder.repository.ScrimHistoryRepository;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendSuggestionScheduler {

    private final ScrimHistoryRepository scrimHistoryRepository;
    private final FriendSuggestionRepository friendSuggestionRepository;
    private final FriendshipRepository friendshipRepository;
    private final BlockRepository blockRepository;

    @Value("${app.friends-finder.suggestion-lookback-days:30}")
    private int lookbackDays;

    @Value("${app.friends-finder.suggestion-purge-days:60}")
    private int purgeDays;

    /**
     * Nightly job: analyse recent scrim history and generate/update friend suggestions.
     *
     * Scoring dimensions (total 0–100):
     *   Interaction frequency  0–30 pts  (scrims together in lookback window)
     *   Mutual friends         0–25 pts  (shared accepted friendships)
     *   Region proximity       0–20 pts  (neutral 8 pts for v1 — PlayerDto lacks region data)
     *   Match quality          0–25 pts  (average matchQualityScore from scrim history)
     */
    @Scheduled(cron = "${app.friends-finder.suggestion-cron:0 0 3 * * *}")
    @Transactional
    public void generateSuggestions() {
        log.info("[FriendSuggestionScheduler] Starting suggestion generation");
        long startTime = System.currentTimeMillis();

        LocalDateTime since = LocalDateTime.now().minusDays(lookbackDays);

        // 1. Load recent scrim history into memory for scoring
        // TODO: Replace findAll() with a targeted query (e.g. findByPlayedAtAfter)
        //       if scrim_history grows beyond ~50k rows
        List<ScrimHistory> recentHistory = scrimHistoryRepository.findAll().stream()
                .filter(s -> s.getPlayedAt() != null && s.getPlayedAt().isAfter(since))
                .toList();

        // Build set of active users
        Set<Long> activeUserIds = new HashSet<>();
        for (ScrimHistory h : recentHistory) {
            activeUserIds.add(h.getUserIdA());
            activeUserIds.add(h.getUserIdB());
        }

        log.info("[FriendSuggestionScheduler] Found {} active users with recent scrim history",
                activeUserIds.size());

        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (Long userId : activeUserIds) {
            List<Long> opponents = scrimHistoryRepository.findRecentOpponents(userId, since);

            for (Long opponentId : opponents) {
                try {
                    // Skip if already friends
                    if (friendshipRepository.areFriends(userId, opponentId)) {
                        skipped++;
                        continue;
                    }

                    // Skip if blocked either way
                    if (blockRepository.isBlockedEitherWay(userId, opponentId)) {
                        skipped++;
                        continue;
                    }

                    // Skip if there's a pending friend request
                    var existingFriendship = friendshipRepository.findBetweenUsers(userId, opponentId);
                    if (existingFriendship.isPresent() && existingFriendship.get().isPending()) {
                        skipped++;
                        continue;
                    }

                    // Compute suggestion score
                    double score = computeSuggestionScore(userId, opponentId, recentHistory);

                    // Determine game mode (most recent shared game)
                    String gameMode = recentHistory.stream()
                            .filter(h -> (h.getUserIdA().equals(userId) && h.getUserIdB().equals(opponentId))
                                      || (h.getUserIdA().equals(opponentId) && h.getUserIdB().equals(userId)))
                            .max(Comparator.comparing(s -> s.getPlayedAt() != null
                                    ? s.getPlayedAt() : LocalDateTime.MIN))
                            .map(ScrimHistory::getGameMode)
                            .orElse(null);

                    int interactionCount = scrimHistoryRepository.countBetweenUsers(userId, opponentId);

                    // Upsert: update if new score is higher, create if doesn't exist
                    Optional<FriendSuggestion> existing = friendSuggestionRepository
                            .findByUserIdAndSuggestedUserId(userId, opponentId);

                    if (existing.isPresent()) {
                        FriendSuggestion s = existing.get();
                        if (score > s.getSuggestionScore()) {
                            s.setSuggestionScore(score);
                            s.setInteractionCount(interactionCount);
                            s.setGameMode(gameMode);
                            s.setDismissed(false); // re-surface if score improved
                            s.setDismissedAt(null);
                            friendSuggestionRepository.save(s);
                            updated++;
                        }
                    } else {
                        List<Long> mutualIds = friendshipRepository.findMutualFriendIds(userId, opponentId);
                        FriendSuggestion s = FriendSuggestion.builder()
                                .userId(userId)
                                .suggestedUserId(opponentId)
                                .suggestionScore(score)
                                .source("SCRIM_OPPONENT")
                                .gameMode(gameMode)
                                .interactionCount(interactionCount)
                                .mutualFriendCount(mutualIds.size())
                                .build();
                        friendSuggestionRepository.save(s);
                        created++;
                    }
                } catch (Exception e) {
                    log.warn("[FriendSuggestionScheduler] Error processing userId={} opponentId={}: {}",
                            userId, opponentId, e.getMessage());
                    skipped++;
                }
            }
        }

        // 2. Purge old unacted suggestions
        LocalDateTime purgeCutoff = LocalDateTime.now().minusDays(purgeDays);
        int purged = friendSuggestionRepository.purgeOlderThan(purgeCutoff);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[FriendSuggestionScheduler] Completed in {}ms — created={} updated={} skipped={} purged={}",
                elapsed, created, updated, skipped, purged);
    }

    /**
     * Computes a suggestion score (0–100) across four dimensions.
     */
    private double computeSuggestionScore(Long userId, Long opponentId,
                                           List<ScrimHistory> recentHistory) {
        double score = 0.0;

        // ── Dimension 1: Interaction frequency (0–30 pts) ─────────────────
        int scrimCount = scrimHistoryRepository.countBetweenUsers(userId, opponentId);
        if (scrimCount >= 3) score += 30.0;
        else if (scrimCount == 2) score += 20.0;
        else if (scrimCount == 1) score += 10.0;

        // ── Dimension 2: Mutual friends (0–25 pts) ────────────────────────
        List<Long> mutualFriendIds = friendshipRepository.findMutualFriendIds(userId, opponentId);
        int mutualCount = mutualFriendIds.size();
        if (mutualCount >= 6) score += 25.0;
        else if (mutualCount >= 3) score += 18.0;
        else if (mutualCount >= 1) score += 10.0;

        // ── Dimension 3: Region proximity (0–20 pts) ──────────────────────
        // PlayerDto currently has only id, user, gamerTag — no country/region field.
        // Return neutral 8 pts. REGION_CLUSTER map is ready for when PlayerDto
        // is extended with country data in a future prompt.
        // TODO: Implement real region scoring when PlayerDto includes country
        score += 8.0;

        // ── Dimension 4: Match quality (0–25 pts) ─────────────────────────
        // Use in-memory recentHistory to avoid extra DB queries
        List<ScrimHistory> sharedHistory = recentHistory.stream()
                .filter(h -> (h.getUserIdA().equals(userId) && h.getUserIdB().equals(opponentId))
                          || (h.getUserIdA().equals(opponentId) && h.getUserIdB().equals(userId)))
                .filter(h -> h.getMatchQualityScore() != null)
                .toList();

        if (!sharedHistory.isEmpty()) {
            double avgQuality = sharedHistory.stream()
                    .mapToDouble(ScrimHistory::getMatchQualityScore)
                    .average()
                    .orElse(0.0);
            score += (avgQuality / 100.0) * 25.0;
        }

        return Math.min(score, 100.0);
    }
}
