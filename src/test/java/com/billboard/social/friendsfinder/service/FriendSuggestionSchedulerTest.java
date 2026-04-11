package com.billboard.social.friendsfinder.service;

import com.billboard.social.friendsfinder.entity.FriendSuggestion;
import com.billboard.social.friendsfinder.entity.ScrimHistory;
import com.billboard.social.friendsfinder.repository.FriendSuggestionRepository;
import com.billboard.social.friendsfinder.repository.ScrimHistoryRepository;
import com.billboard.social.graph.entity.Friendship;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.FriendshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendSuggestionSchedulerTest {

    @Mock private ScrimHistoryRepository scrimHistoryRepository;
    @Mock private FriendSuggestionRepository friendSuggestionRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private BlockRepository blockRepository;

    @InjectMocks
    private FriendSuggestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "lookbackDays", 30);
        ReflectionTestUtils.setField(scheduler, "purgeDays", 60);
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private ScrimHistory scrim(Long userA, Long userB, Double quality, LocalDateTime playedAt) {
        return ScrimHistory.builder()
                .id(UUID.randomUUID())
                .userIdA(userA).userIdB(userB)
                .esportsMatchId((long) (Math.random() * 10000))
                .gameMode("5v5")
                .matchQualityScore(quality)
                .playedAt(playedAt)
                .build();
    }

    // ── computeSuggestionScore (tested indirectly via generateSuggestions) ──

    @Nested
    class ScoringDimensions {

        private final LocalDateTime recent = LocalDateTime.now().minusDays(5);

        @Test
        void interactionFrequency_oneScrimmage_gives10Points() {
            ScrimHistory h = scrim(1L, 2L, null, recent);
            when(scrimHistoryRepository.findAll()).thenReturn(List.of(h));
            when(scrimHistoryRepository.findRecentOpponents(eq(1L), any())).thenReturn(List.of(2L));
            when(scrimHistoryRepository.findRecentOpponents(eq(2L), any())).thenReturn(List.of(1L));
            when(scrimHistoryRepository.countBetweenUsers(anyLong(), anyLong())).thenReturn(1);
            when(friendshipRepository.areFriends(anyLong(), anyLong())).thenReturn(false);
            when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(Optional.empty());
            when(friendSuggestionRepository.findByUserIdAndSuggestedUserId(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findMutualFriendIds(anyLong(), anyLong())).thenReturn(List.of());

            scheduler.generateSuggestions();

            ArgumentCaptor<FriendSuggestion> captor = ArgumentCaptor.forClass(FriendSuggestion.class);
            verify(friendSuggestionRepository, atLeastOnce()).save(captor.capture());

            // 1 scrim = 10 pts interaction + 8 pts region (neutral) + 0 mutual + 0 quality (null) = 18
            FriendSuggestion saved = captor.getAllValues().stream()
                    .filter(s -> s.getUserId().equals(1L) && s.getSuggestedUserId().equals(2L))
                    .findFirst().orElseThrow();
            assertThat(saved.getSuggestionScore()).isEqualTo(18.0);
        }

        @Test
        void interactionFrequency_threeScrimmages_gives30Points() {
            List<ScrimHistory> history = List.of(
                    scrim(1L, 2L, null, recent),
                    scrim(1L, 2L, null, recent.minusDays(1)),
                    scrim(2L, 1L, null, recent.minusDays(2))
            );
            when(scrimHistoryRepository.findAll()).thenReturn(history);
            when(scrimHistoryRepository.findRecentOpponents(eq(1L), any())).thenReturn(List.of(2L));
            when(scrimHistoryRepository.findRecentOpponents(eq(2L), any())).thenReturn(List.of(1L));
            when(scrimHistoryRepository.countBetweenUsers(anyLong(), anyLong())).thenReturn(3);
            when(friendshipRepository.areFriends(anyLong(), anyLong())).thenReturn(false);
            when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(Optional.empty());
            when(friendSuggestionRepository.findByUserIdAndSuggestedUserId(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findMutualFriendIds(anyLong(), anyLong())).thenReturn(List.of());

            scheduler.generateSuggestions();

            ArgumentCaptor<FriendSuggestion> captor = ArgumentCaptor.forClass(FriendSuggestion.class);
            verify(friendSuggestionRepository, atLeastOnce()).save(captor.capture());

            // 3+ scrims = 30 pts + 8 neutral = 38
            FriendSuggestion saved = captor.getAllValues().stream()
                    .filter(s -> s.getUserId().equals(1L) && s.getSuggestedUserId().equals(2L))
                    .findFirst().orElseThrow();
            assertThat(saved.getSuggestionScore()).isEqualTo(38.0);
        }

        @Test
        void mutualFriends_sixOrMore_gives25Points() {
            ScrimHistory h = scrim(1L, 2L, null, recent);
            when(scrimHistoryRepository.findAll()).thenReturn(List.of(h));
            when(scrimHistoryRepository.findRecentOpponents(eq(1L), any())).thenReturn(List.of(2L));
            when(scrimHistoryRepository.findRecentOpponents(eq(2L), any())).thenReturn(List.of(1L));
            when(scrimHistoryRepository.countBetweenUsers(anyLong(), anyLong())).thenReturn(1);
            when(friendshipRepository.areFriends(anyLong(), anyLong())).thenReturn(false);
            when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(Optional.empty());
            when(friendSuggestionRepository.findByUserIdAndSuggestedUserId(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            // 6 mutual friends
            when(friendshipRepository.findMutualFriendIds(anyLong(), anyLong()))
                    .thenReturn(List.of(10L, 20L, 30L, 40L, 50L, 60L));

            scheduler.generateSuggestions();

            ArgumentCaptor<FriendSuggestion> captor = ArgumentCaptor.forClass(FriendSuggestion.class);
            verify(friendSuggestionRepository, atLeastOnce()).save(captor.capture());

            // 10 interaction + 25 mutual + 8 region = 43
            FriendSuggestion saved = captor.getAllValues().stream()
                    .filter(s -> s.getUserId().equals(1L) && s.getSuggestedUserId().equals(2L))
                    .findFirst().orElseThrow();
            assertThat(saved.getSuggestionScore()).isEqualTo(43.0);
        }

        @Test
        void matchQuality_averagedCorrectly() {
            List<ScrimHistory> history = List.of(
                    scrim(1L, 2L, 60.0, recent),
                    scrim(1L, 2L, 80.0, recent.minusDays(1))
            );
            when(scrimHistoryRepository.findAll()).thenReturn(history);
            when(scrimHistoryRepository.findRecentOpponents(eq(1L), any())).thenReturn(List.of(2L));
            when(scrimHistoryRepository.findRecentOpponents(eq(2L), any())).thenReturn(List.of(1L));
            when(scrimHistoryRepository.countBetweenUsers(anyLong(), anyLong())).thenReturn(2);
            when(friendshipRepository.areFriends(anyLong(), anyLong())).thenReturn(false);
            when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(Optional.empty());
            when(friendSuggestionRepository.findByUserIdAndSuggestedUserId(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findMutualFriendIds(anyLong(), anyLong())).thenReturn(List.of());

            scheduler.generateSuggestions();

            ArgumentCaptor<FriendSuggestion> captor = ArgumentCaptor.forClass(FriendSuggestion.class);
            verify(friendSuggestionRepository, atLeastOnce()).save(captor.capture());

            // 2 scrims = 20 + 8 region + 0 mutual + (avg(60,80)=70 → 70/100*25=17.5) = 45.5
            FriendSuggestion saved = captor.getAllValues().stream()
                    .filter(s -> s.getUserId().equals(1L) && s.getSuggestedUserId().equals(2L))
                    .findFirst().orElseThrow();
            assertThat(saved.getSuggestionScore()).isEqualTo(45.5);
        }

        @Test
        void regionProximity_alwaysNeutral8ForV1() {
            ScrimHistory h = scrim(1L, 2L, 100.0, recent);
            when(scrimHistoryRepository.findAll()).thenReturn(List.of(h));
            when(scrimHistoryRepository.findRecentOpponents(eq(1L), any())).thenReturn(List.of(2L));
            when(scrimHistoryRepository.findRecentOpponents(eq(2L), any())).thenReturn(List.of(1L));
            when(scrimHistoryRepository.countBetweenUsers(anyLong(), anyLong())).thenReturn(1);
            when(friendshipRepository.areFriends(anyLong(), anyLong())).thenReturn(false);
            when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(Optional.empty());
            when(friendSuggestionRepository.findByUserIdAndSuggestedUserId(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findMutualFriendIds(anyLong(), anyLong())).thenReturn(List.of());

            scheduler.generateSuggestions();

            ArgumentCaptor<FriendSuggestion> captor = ArgumentCaptor.forClass(FriendSuggestion.class);
            verify(friendSuggestionRepository, atLeastOnce()).save(captor.capture());

            // 10 interaction + 8 region + 0 mutual + (100/100*25=25) = 43
            FriendSuggestion saved = captor.getAllValues().stream()
                    .filter(s -> s.getUserId().equals(1L) && s.getSuggestedUserId().equals(2L))
                    .findFirst().orElseThrow();
            assertThat(saved.getSuggestionScore()).isEqualTo(43.0);
        }

        @Test
        void scoreCapped_at100() {
            // Max possible in v1: 30 + 25 + 8 + 25 = 88 — verify cap exists and score is correct
            List<ScrimHistory> history = List.of(
                    scrim(1L, 2L, 100.0, LocalDateTime.now().minusDays(1)),
                    scrim(1L, 2L, 100.0, LocalDateTime.now().minusDays(2)),
                    scrim(2L, 1L, 100.0, LocalDateTime.now().minusDays(3))
            );
            when(scrimHistoryRepository.findAll()).thenReturn(history);
            when(scrimHistoryRepository.findRecentOpponents(anyLong(), any())).thenReturn(List.of(2L));
            when(scrimHistoryRepository.countBetweenUsers(anyLong(), anyLong())).thenReturn(3);
            when(friendshipRepository.areFriends(anyLong(), anyLong())).thenReturn(false);
            when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(Optional.empty());
            when(friendSuggestionRepository.findByUserIdAndSuggestedUserId(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findMutualFriendIds(anyLong(), anyLong()))
                    .thenReturn(List.of(10L, 20L, 30L, 40L, 50L, 60L)); // 6 mutual = 25 pts

            scheduler.generateSuggestions();

            ArgumentCaptor<FriendSuggestion> captor = ArgumentCaptor.forClass(FriendSuggestion.class);
            verify(friendSuggestionRepository, atLeastOnce()).save(captor.capture());

            // 30 + 25 + 8 + 25 = 88 ≤ 100 — verify cap is not exceeded
            FriendSuggestion saved = captor.getAllValues().stream()
                    .filter(s -> s.getUserId().equals(1L) && s.getSuggestedUserId().equals(2L))
                    .findFirst().orElseThrow();
            assertThat(saved.getSuggestionScore()).isLessThanOrEqualTo(100.0);
            assertThat(saved.getSuggestionScore()).isEqualTo(88.0);
        }
    }

    // ── Skip conditions ──────────────────────────────────────────────────

    @Nested
    class SkipConditions {

        private final LocalDateTime recent = LocalDateTime.now().minusDays(5);

        @Test
        void alreadyFriends_skipped() {
            ScrimHistory h = scrim(1L, 2L, 50.0, recent);
            when(scrimHistoryRepository.findAll()).thenReturn(List.of(h));
            when(scrimHistoryRepository.findRecentOpponents(anyLong(), any())).thenReturn(List.of(2L));
            when(friendshipRepository.areFriends(1L, 2L)).thenReturn(true);
            when(friendshipRepository.areFriends(2L, 1L)).thenReturn(true);

            scheduler.generateSuggestions();

            verify(friendSuggestionRepository, never()).save(any());
        }

        @Test
        void blockedUsers_skipped() {
            ScrimHistory h = scrim(1L, 2L, 50.0, recent);
            when(scrimHistoryRepository.findAll()).thenReturn(List.of(h));
            when(scrimHistoryRepository.findRecentOpponents(anyLong(), any())).thenReturn(List.of(2L));
            when(friendshipRepository.areFriends(anyLong(), anyLong())).thenReturn(false);
            when(blockRepository.isBlockedEitherWay(1L, 2L)).thenReturn(true);
            when(blockRepository.isBlockedEitherWay(2L, 1L)).thenReturn(true);

            scheduler.generateSuggestions();

            verify(friendSuggestionRepository, never()).save(any());
        }

        @Test
        void pendingFriendRequest_skipped() {
            ScrimHistory h = scrim(1L, 2L, 50.0, recent);
            when(scrimHistoryRepository.findAll()).thenReturn(List.of(h));
            when(scrimHistoryRepository.findRecentOpponents(anyLong(), any())).thenReturn(List.of(2L));
            when(friendshipRepository.areFriends(anyLong(), anyLong())).thenReturn(false);
            when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);

            Friendship pending = mock(Friendship.class);
            when(pending.isPending()).thenReturn(true);
            when(friendshipRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(Optional.of(pending));

            scheduler.generateSuggestions();

            verify(friendSuggestionRepository, never()).save(any());
        }
    }

    // ── Upsert behavior ──────────────────────────────────────────────────

    @Nested
    class UpsertBehavior {

        private final LocalDateTime recent = LocalDateTime.now().minusDays(5);

        @Test
        void existingSuggestion_higherScore_updatesAndResurfaces() {
            ScrimHistory h = scrim(1L, 2L, 80.0, recent);
            when(scrimHistoryRepository.findAll()).thenReturn(List.of(h));
            when(scrimHistoryRepository.findRecentOpponents(eq(1L), any())).thenReturn(List.of(2L));
            when(scrimHistoryRepository.findRecentOpponents(eq(2L), any())).thenReturn(List.of(1L));
            when(scrimHistoryRepository.countBetweenUsers(anyLong(), anyLong())).thenReturn(1);
            when(friendshipRepository.areFriends(anyLong(), anyLong())).thenReturn(false);
            when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(Optional.empty());
            when(friendshipRepository.findMutualFriendIds(anyLong(), anyLong())).thenReturn(List.of());

            FriendSuggestion existing = FriendSuggestion.builder()
                    .id(UUID.randomUUID()).userId(1L).suggestedUserId(2L)
                    .suggestionScore(5.0) // low old score
                    .source("SCRIM_OPPONENT").dismissed(true)
                    .dismissedAt(LocalDateTime.now().minusDays(3))
                    .build();
            when(friendSuggestionRepository.findByUserIdAndSuggestedUserId(1L, 2L))
                    .thenReturn(Optional.of(existing));
            // For user 2 → 1 direction, return empty so it creates new
            when(friendSuggestionRepository.findByUserIdAndSuggestedUserId(2L, 1L))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findMutualFriendIds(2L, 1L)).thenReturn(List.of());

            scheduler.generateSuggestions();

            // The existing suggestion should be updated and re-surfaced
            assertThat(existing.getDismissed()).isFalse();
            assertThat(existing.getDismissedAt()).isNull();
            assertThat(existing.getSuggestionScore()).isGreaterThan(5.0);
        }

        @Test
        void existingSuggestion_lowerScore_notDowngraded() {
            ScrimHistory h = scrim(1L, 2L, null, recent); // null quality
            when(scrimHistoryRepository.findAll()).thenReturn(List.of(h));
            when(scrimHistoryRepository.findRecentOpponents(eq(1L), any())).thenReturn(List.of(2L));
            when(scrimHistoryRepository.findRecentOpponents(eq(2L), any())).thenReturn(List.of(1L));
            when(scrimHistoryRepository.countBetweenUsers(anyLong(), anyLong())).thenReturn(1);
            when(friendshipRepository.areFriends(anyLong(), anyLong())).thenReturn(false);
            when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(Optional.empty());
            when(friendshipRepository.findMutualFriendIds(anyLong(), anyLong())).thenReturn(List.of());

            FriendSuggestion existing = FriendSuggestion.builder()
                    .id(UUID.randomUUID()).userId(1L).suggestedUserId(2L)
                    .suggestionScore(99.0) // high old score
                    .source("SCRIM_OPPONENT").dismissed(false)
                    .build();
            when(friendSuggestionRepository.findByUserIdAndSuggestedUserId(1L, 2L))
                    .thenReturn(Optional.of(existing));
            when(friendSuggestionRepository.findByUserIdAndSuggestedUserId(2L, 1L))
                    .thenReturn(Optional.empty());

            scheduler.generateSuggestions();

            // Score should remain at 99.0 — new score (18.0) is lower, so no update
            assertThat(existing.getSuggestionScore()).isEqualTo(99.0);
        }
    }

    // ── Purge ────────────────────────────────────────────────────────────

    @Test
    void generateSuggestions_purgesOldSuggestions() {
        when(scrimHistoryRepository.findAll()).thenReturn(List.of());
        when(friendSuggestionRepository.purgeOlderThan(any(LocalDateTime.class))).thenReturn(5);

        scheduler.generateSuggestions();

        verify(friendSuggestionRepository).purgeOlderThan(any(LocalDateTime.class));
    }

    // ── No scrim history ─────────────────────────────────────────────────

    @Test
    void generateSuggestions_noRecentHistory_onlyPurges() {
        when(scrimHistoryRepository.findAll()).thenReturn(List.of());
        when(friendSuggestionRepository.purgeOlderThan(any())).thenReturn(0);

        scheduler.generateSuggestions();

        // Should still purge, but no suggestions created
        verify(friendSuggestionRepository).purgeOlderThan(any());
        verify(friendSuggestionRepository, never()).save(any());
    }
}
