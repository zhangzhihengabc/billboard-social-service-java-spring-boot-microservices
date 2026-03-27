package com.billboard.social.gamegroup.event;

import com.billboard.social.gamegroup.entity.GameGroupProfile;
import com.billboard.social.gamegroup.repository.GameGroupProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScrimResultConsumer {

    private final GameGroupProfileRepository gameGroupProfileRepository;

    /**
     * Processes game.group.scrim.completed events.
     * Updates GameGroupProfile stats: scrimCount, winRate, averageElo.
     */
    @KafkaListener(
            topics = "game.group.scrim.completed",
            groupId = "${spring.kafka.consumer.group-id:games-groups-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleScrimCompleted(Map<String, Object> event) {
        try {
            String groupIdStr = (String) event.get("groupId");
            if (groupIdStr == null) {
                log.warn("ScrimResultConsumer: missing groupId in event");
                return;
            }

            UUID groupId = UUID.fromString(groupIdStr);
            Boolean won = (Boolean) event.get("won");
            Integer eloChange = event.get("eloChange") instanceof Number
                    ? ((Number) event.get("eloChange")).intValue()
                    : 0;

            gameGroupProfileRepository.findByGroupId(groupId).ifPresent(profile -> {
                updateStats(profile, won != null && won, eloChange);
                gameGroupProfileRepository.save(profile);
                log.info("ScrimResultConsumer: updated stats for groupId={} won={} eloChange={}",
                        groupId, won, eloChange);
            });

        } catch (Exception e) {
            log.error("ScrimResultConsumer: failed to process event: {}", e.getMessage(), e);
        }
    }

    private void updateStats(GameGroupProfile profile, boolean won, int eloChange) {
        int newScrimCount = profile.getScrimCount() + 1;
        profile.setScrimCount(newScrimCount);

        // Recalculate win rate
        BigDecimal currentWinRate = profile.getWinRate() != null
                ? profile.getWinRate()
                : BigDecimal.ZERO;

        // wins = winRate * (scrimCount - 1) + (won ? 1 : 0)
        BigDecimal previousWins = currentWinRate
                .multiply(BigDecimal.valueOf(newScrimCount - 1))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        BigDecimal totalWins = previousWins.add(won ? BigDecimal.ONE : BigDecimal.ZERO);
        BigDecimal newWinRate = totalWins
                .divide(BigDecimal.valueOf(newScrimCount), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        profile.setWinRate(newWinRate);

        // Update average ELO
        if (eloChange != 0) {
            int currentElo = profile.getAverageElo() != null ? profile.getAverageElo() : 1000;
            profile.setAverageElo(currentElo + eloChange);
        }
    }
}