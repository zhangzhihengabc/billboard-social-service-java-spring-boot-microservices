package com.billboard.social.graph.event;

import com.billboard.social.graph.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SocialEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange:social-events}")
    private String exchange;

    // Follow events
    public void publishFollowed(Follow follow) {
        Map<String, Object> event = createBaseEvent("social.followed");
        event.put("followId", follow.getId());
        event.put("followerId", follow.getFollowerId());
        event.put("followingId", follow.getFollowingId());
        event.put("isCloseFriend", follow.getIsCloseFriend());
        publish("social.followed", event);
    }

    public void publishUnfollowed(Long followerId, Long followingId) {
        Map<String, Object> event = createBaseEvent("social.unfollowed");
        event.put("followerId", followerId);
        event.put("followingId", followingId);
        publish("social.unfollowed", event);
    }

    // Friendship events
    public void publishFriendRequestSent(Friendship friendship) {
        Map<String, Object> event = createBaseEvent("social.friend_request.sent");
        event.put("friendshipId", friendship.getId());
        event.put("requesterId", friendship.getRequesterId());
        event.put("addresseeId", friendship.getAddresseeId());
        publish("social.friend_request.sent", event);
    }

    public void publishFriendRequestAccepted(Friendship friendship) {
        Map<String, Object> event = createBaseEvent("social.friend_request.accepted");
        event.put("friendshipId", friendship.getId());
        event.put("requesterId", friendship.getRequesterId());
        event.put("addresseeId", friendship.getAddresseeId());
        publish("social.friend_request.accepted", event);
    }

    public void publishFriendRequestDeclined(Friendship friendship) {
        Map<String, Object> event = createBaseEvent("social.friend_request.declined");
        event.put("friendshipId", friendship.getId());
        event.put("requesterId", friendship.getRequesterId());
        event.put("addresseeId", friendship.getAddresseeId());
        publish("social.friend_request.declined", event);
    }

    public void publishUnfriended(Long userId1, Long userId2) {
        Map<String, Object> event = createBaseEvent("social.unfriended");
        event.put("userId1", userId1);
        event.put("userId2", userId2);
        publish("social.unfriended", event);
    }

    // Block events
    public void publishBlocked(Block block) {
        Map<String, Object> event = createBaseEvent("social.blocked");
        event.put("blockId", block.getId());
        event.put("blockerId", block.getBlockerId());
        event.put("blockedId", block.getBlockedId());
        publish("social.blocked", event);
    }

    public void publishUserBlocked(Block block) {
        publishBlocked(block);
    }

    public void publishUnblocked(Long blockerId, Long blockedId) {
        Map<String, Object> event = createBaseEvent("social.unblocked");
        event.put("blockerId", blockerId);
        event.put("blockedId", blockedId);
        publish("social.unblocked", event);
    }

    public void publishUserUnblocked(Long blockerId, Long blockedId) {
        publishUnblocked(blockerId, blockedId);
    }

    // Reaction events
    public void publishReactionAdded(Reaction reaction) {
        Map<String, Object> event = createBaseEvent("social.reaction.added");
        event.put("reactionId", reaction.getId());
        event.put("userId", reaction.getUserId());
        event.put("contentType", reaction.getContentType().name());
        event.put("contentId", reaction.getContentId());
        event.put("reactionType", reaction.getReactionType().name());
        publish("social.reaction.added", event);
    }

    public void publishReactionRemoved(Long userId, String contentType, UUID contentId) {
        Map<String, Object> event = createBaseEvent("social.reaction.removed");
        event.put("userId", userId);
        event.put("contentType", contentType);
        event.put("contentId", contentId);
        publish("social.reaction.removed", event);
    }

    public void publishReactionRemoved(Reaction reaction) {
        publishReactionRemoved(reaction.getUserId(), reaction.getContentType().name(), reaction.getContentId());
    }

    // Share events
    public void publishShared(Share share) {
        Map<String, Object> event = createBaseEvent("social.shared");
        event.put("shareId", share.getId());
        event.put("userId", share.getUserId());
        event.put("contentType", share.getContentType().name());
        event.put("contentId", share.getContentId());
        event.put("isPrivate", share.getIsPrivateShare());
        publish("social.shared", event);
    }

    public void publishContentShared(Share share) {
        publishShared(share);
    }

    // Poke events
    public void publishPoked(Poke poke) {
        Map<String, Object> event = createBaseEvent("social.poked");
        event.put("pokeId", poke.getId());
        event.put("pokerId", poke.getPokerId());
        event.put("pokedId", poke.getPokedId());
        event.put("pokeCount", poke.getPokeCount());
        publish("social.poked", event);
    }

    public void publishUserPoked(Poke poke) {
        publishPoked(poke);
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
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.debug("Published event: {} with routing key: {}", event.get("eventType"), routingKey);
        } catch (Exception e) {
            log.error("Failed to publish event: {} - {}", event.get("eventType"), e.getMessage());
        }
    }
}
