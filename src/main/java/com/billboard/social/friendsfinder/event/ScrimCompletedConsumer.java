package com.billboard.social.friendsfinder.event;

import com.billboard.social.friendsfinder.entity.ScrimHistory;
import com.billboard.social.friendsfinder.repository.ScrimHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScrimCompletedConsumer {

    private final ScrimHistoryRepository scrimHistoryRepository;

    @KafkaListener(
            topics = "esports.scrim.completed",
            groupId = "${spring.kafka.consumer.group-id:billboard-social-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleScrimCompleted(Map<String, Object> event) {
        try {
            Long matchId = toLong(event.get("matchId"));
            String userAStr = (String) event.get("lobbyALeaderUserId");
            String userBStr = (String) event.get("lobbyBLeaderUserId");

            if (userAStr == null || userBStr == null) {
                log.warn("ScrimCompletedConsumer: missing leader user IDs in event");
                return;
            }

            Long userIdA = Long.valueOf(userAStr);
            Long userIdB = Long.valueOf(userBStr);

            // Idempotency: skip if we've already processed this match
            if (matchId != null && scrimHistoryRepository.existsByEsportsMatchId(matchId)) {
                log.debug("ScrimCompletedConsumer: matchId={} already processed, skipping", matchId);
                return;
            }

            String gameMode = (String) event.get("gameMode");
            Double matchQuality = toDouble(event.get("matchQualityScore"));
            String agreedTimeStr = (String) event.get("agreedStartTime");
            LocalDateTime playedAt = agreedTimeStr != null
                    ? LocalDateTime.parse(agreedTimeStr)
                    : LocalDateTime.now();

            ScrimHistory history = ScrimHistory.builder()
                    .userIdA(userIdA)
                    .userIdB(userIdB)
                    .esportsMatchId(matchId)
                    .gameMode(gameMode)
                    .matchQualityScore(matchQuality)
                    .playedAt(playedAt)
                    .build();

            scrimHistoryRepository.save(history);

            log.info("ScrimCompletedConsumer: recorded scrim history matchId={} userA={} userB={} game={}",
                    matchId, userIdA, userIdB, gameMode);

        } catch (Exception e) {
            log.error("ScrimCompletedConsumer: failed to process event: {}", e.getMessage(), e);
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.valueOf(value.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.valueOf(value.toString()); }
        catch (NumberFormatException e) { return null; }
    }
}
