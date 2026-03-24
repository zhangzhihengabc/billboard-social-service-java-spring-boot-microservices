package com.billboard.social.gamegroup.event;

import com.billboard.social.gamegroup.service.GameGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes group.chat.channel.created events published by the social chat service.
 *
 * Flow:
 *   1. GameGroupService.createGameGroup() fires GROUP_CHAT_REQUESTED via GameGroupEventPublisher.
 *   2. The social chat service processes that event and creates a dedicated group chat channel.
 *   3. The chat service publishes group.chat.channel.created with { groupId, chatChannelId }.
 *   4. This consumer receives that event and stores the chatChannelId on GameGroupProfile.
 *   5. Clients can then retrieve the channel via GET /api/v1/game-groups/{id}/chat-channel.
 *
 * Failures are logged but not re-thrown so they do not disrupt the Kafka consumer thread.
 * A dead-letter queue can be configured on the Kafka topic for retry handling in production.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GroupChatChannelConsumer {

    private final GameGroupService gameGroupService;

    @KafkaListener(topics = "group.chat.channel.created", groupId = "games-groups-service")
    public void onGroupChatChannelCreated(Map<String, Object> event) {
        try {
            String groupIdStr   = (String) event.get("groupId");
            String chatChannelId = (String) event.get("chatChannelId");

            if (groupIdStr == null || chatChannelId == null) {
                log.warn("Received malformed group.chat.channel.created event — missing required fields: {}", event);
                return;
            }

            UUID groupId = UUID.fromString(groupIdStr);
            gameGroupService.updateChatChannelId(groupId, chatChannelId);

            log.debug("Processed group.chat.channel.created: groupId={} channelId={}", groupId, chatChannelId);

        } catch (IllegalArgumentException e) {
            log.error("Invalid groupId UUID in group.chat.channel.created event: {}", event, e);
        } catch (Exception e) {
            log.error("Failed to process group.chat.channel.created event: {}", e.getMessage(), e);
        }
    }
}