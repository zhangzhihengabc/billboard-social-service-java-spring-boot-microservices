package com.billboard.social.gamegroup.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GameGroupEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic:social-events}")
    private String topic;

    public void publishLfsBroadcast(UUID groupId, String gameTag, String region, List<Long> memberIds, String message) {
        Map<String, Object> event = createBaseEvent("game.group.lfs.broadcast");
        event.put("groupId", groupId);
        event.put("gameTag", gameTag);
        event.put("region", region);
        event.put("memberIds", memberIds);
        event.put("message", message);
        publish("game.group.lfs.broadcast", event);
    }

    public void publishLfsCancelled(UUID groupId, String gameTag) {
        Map<String, Object> event = createBaseEvent("game.group.lfs.cancelled");
        event.put("groupId", groupId);
        event.put("gameTag", gameTag);
        publish("game.group.lfs.cancelled", event);
    }

    public void publishLfsMatched(UUID groupId, UUID matchedGroupId, String gameTag) {
        Map<String, Object> event = createBaseEvent("game.group.lfs.matched");
        event.put("groupId", groupId);
        event.put("matchedGroupId", matchedGroupId);
        event.put("gameTag", gameTag);
        publish("game.group.lfs.matched", event);
    }

    public void publishScrimCompleted(UUID groupId, String gameTag, boolean won, Integer eloChange) {
        Map<String, Object> event = createBaseEvent("game.group.scrim.completed");
        event.put("groupId", groupId);
        event.put("gameTag", gameTag);
        event.put("won", won);
        event.put("eloChange", eloChange);
        publish("game.group.scrim.completed", event);
    }

    public void publishTournamentJoined(UUID groupId, Long teamId, String gameTag) {
        Map<String, Object> event = createBaseEvent("game.group.tournament.joined");
        event.put("groupId", groupId);
        event.put("teamId", teamId);
        event.put("gameTag", gameTag);
        publish("game.group.tournament.joined", event);
    }

    public void publishMemberLinkedAccount(UUID groupId, Long userId, String gameTag, String gameAccountId) {
        Map<String, Object> event = createBaseEvent("game.group.member.account_linked");
        event.put("groupId", groupId);
        event.put("userId", userId);
        event.put("gameTag", gameTag);
        event.put("gameAccountId", gameAccountId);
        publish("game.group.member.account_linked", event);
    }

    public void publishGroupChatRequested(UUID groupId, List<Long> memberIds) {
        Map<String, Object> event = createBaseEvent("game.group.chat.requested");
        event.put("groupId", groupId);
        event.put("memberIds", memberIds);
        publish("game.group.chat.requested", event);
    }

    private Map<String, Object> createBaseEvent(String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("eventId", UUID.randomUUID().toString());
        return event;
    }

    private void publish(String routingKey, Map<String, Object> event) {
        try {
            kafkaTemplate.send(topic, routingKey, event);
            log.debug("Published game group event: {} with routing key: {}", event.get("eventType"), routingKey);
        } catch (Exception e) {
            log.error("Failed to publish game group event: {} - {}", event.get("eventType"), e.getMessage());
        }
    }
}