package com.billboard.social.friendsfinder.event;

import com.billboard.social.friendsfinder.entity.ScrimHistory;
import com.billboard.social.friendsfinder.repository.ScrimHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrimCompletedConsumerTest {

    @Mock
    private ScrimHistoryRepository scrimHistoryRepository;

    @InjectMocks
    private ScrimCompletedConsumer consumer;

    // ── Helper ───────────────────────────────────────────────────────────

    private Map<String, Object> validEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("matchId", 100L);
        event.put("lobbyALeaderUserId", "42");
        event.put("lobbyBLeaderUserId", "55");
        event.put("gameMode", "5v5");
        event.put("matchQualityScore", 78.3);
        event.put("agreedStartTime", "2025-06-15T14:30:00");
        return event;
    }

    // ── Happy path ───────────────────────────────────────────────────────

    @Test
    void handleScrimCompleted_validEvent_savesScrimHistory() {
        when(scrimHistoryRepository.existsByEsportsMatchId(100L)).thenReturn(false);

        consumer.handleScrimCompleted(validEvent());

        ArgumentCaptor<ScrimHistory> captor = ArgumentCaptor.forClass(ScrimHistory.class);
        verify(scrimHistoryRepository).save(captor.capture());

        ScrimHistory saved = captor.getValue();
        assertThat(saved.getUserIdA()).isEqualTo(42L);
        assertThat(saved.getUserIdB()).isEqualTo(55L);
        assertThat(saved.getEsportsMatchId()).isEqualTo(100L);
        assertThat(saved.getGameMode()).isEqualTo("5v5");
        assertThat(saved.getMatchQualityScore()).isEqualTo(78.3);
        assertThat(saved.getPlayedAt()).isEqualTo(LocalDateTime.parse("2025-06-15T14:30:00"));
    }

    // ── Idempotency ──────────────────────────────────────────────────────

    @Test
    void handleScrimCompleted_duplicateMatchId_skipsWithoutSaving() {
        when(scrimHistoryRepository.existsByEsportsMatchId(100L)).thenReturn(true);

        consumer.handleScrimCompleted(validEvent());

        verify(scrimHistoryRepository, never()).save(any());
    }

    // ── Missing leader IDs ───────────────────────────────────────────────

    @Test
    void handleScrimCompleted_missingUserA_skips() {
        Map<String, Object> event = validEvent();
        event.put("lobbyALeaderUserId", null);

        consumer.handleScrimCompleted(event);

        verify(scrimHistoryRepository, never()).save(any());
    }

    @Test
    void handleScrimCompleted_missingUserB_skips() {
        Map<String, Object> event = validEvent();
        event.put("lobbyBLeaderUserId", null);

        consumer.handleScrimCompleted(event);

        verify(scrimHistoryRepository, never()).save(any());
    }

    // ── Null matchId (still saves — no idempotency key) ──────────────────

    @Test
    void handleScrimCompleted_nullMatchId_stillSaves() {
        Map<String, Object> event = validEvent();
        event.put("matchId", null);

        consumer.handleScrimCompleted(event);

        verify(scrimHistoryRepository).save(any(ScrimHistory.class));
    }

    // ── Type coercion edge cases ─────────────────────────────────────────

    @Test
    void handleScrimCompleted_matchIdAsInteger_handledCorrectly() {
        // Kafka JSON deserialization may produce Integer instead of Long
        Map<String, Object> event = validEvent();
        event.put("matchId", Integer.valueOf(100)); // not Long
        when(scrimHistoryRepository.existsByEsportsMatchId(100L)).thenReturn(false);

        consumer.handleScrimCompleted(event);

        verify(scrimHistoryRepository).save(any(ScrimHistory.class));
    }

    @Test
    void handleScrimCompleted_matchQualityScoreAsInteger_handledCorrectly() {
        Map<String, Object> event = validEvent();
        event.put("matchQualityScore", Integer.valueOf(80)); // not Double
        when(scrimHistoryRepository.existsByEsportsMatchId(100L)).thenReturn(false);

        consumer.handleScrimCompleted(event);

        ArgumentCaptor<ScrimHistory> captor = ArgumentCaptor.forClass(ScrimHistory.class);
        verify(scrimHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getMatchQualityScore()).isEqualTo(80.0);
    }

    // ── Missing optional fields ──────────────────────────────────────────

    @Test
    void handleScrimCompleted_noAgreedStartTime_usesNow() {
        Map<String, Object> event = validEvent();
        event.put("agreedStartTime", null);
        when(scrimHistoryRepository.existsByEsportsMatchId(100L)).thenReturn(false);

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        consumer.handleScrimCompleted(event);
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        ArgumentCaptor<ScrimHistory> captor = ArgumentCaptor.forClass(ScrimHistory.class);
        verify(scrimHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getPlayedAt()).isBetween(before, after);
    }

    @Test
    void handleScrimCompleted_nullMatchQuality_savedAsNull() {
        Map<String, Object> event = validEvent();
        event.put("matchQualityScore", null);
        when(scrimHistoryRepository.existsByEsportsMatchId(100L)).thenReturn(false);

        consumer.handleScrimCompleted(event);

        ArgumentCaptor<ScrimHistory> captor = ArgumentCaptor.forClass(ScrimHistory.class);
        verify(scrimHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getMatchQualityScore()).isNull();
    }

    // ── Malformed data doesn't crash ─────────────────────────────────────

    @Test
    void handleScrimCompleted_malformedTimestamp_doesNotThrow() {
        Map<String, Object> event = validEvent();
        event.put("agreedStartTime", "not-a-timestamp");
        when(scrimHistoryRepository.existsByEsportsMatchId(100L)).thenReturn(false);

        // Should not throw — caught by outer try/catch
        consumer.handleScrimCompleted(event);

        // The key assertion is no unhandled exception propagates.
    }
}
