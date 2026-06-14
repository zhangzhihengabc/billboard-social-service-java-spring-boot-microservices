package com.billboard.social.graph.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.graph.dto.response.SocialResponses.FriendPresenceResponse;
import com.billboard.social.graph.repository.FriendshipRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    // Redis key pattern: "presence:{userId}"
    private static final String PRESENCE_KEY_PREFIX = "presence:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final FriendshipRepository friendshipRepository;
    private final UserServiceClient userServiceClient;

    // Configurable via application.yml; defaults to 60 seconds.
    // The client must re-send a heartbeat before this window expires to remain
    // visible as online.
    @Value("${app.presence.ttl-seconds:60}")
    private long presenceTtlSeconds;

    /**
     * Marks the given user as online by writing a Redis key with a TTL.
     * Called by the heartbeat endpoint. If the client stops sending heartbeats
     * (app backgrounded, network lost) the key expires and the user becomes
     * offline automatically — no cleanup job required.
     */
    public void markOnline(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, "ONLINE", presenceTtlSeconds, TimeUnit.SECONDS);
        log.debug("User {} marked as online (TTL: {}s)", userId, presenceTtlSeconds);
    }

    /**
     * Returns true if the given user currently has an active presence key in Redis.
     */
    public boolean isOnline(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        // redisTemplate.hasKey() returns Boolean (nullable) — not boolean.
        // Boolean.TRUE.equals() is null-safe: returns false for null, false for FALSE,
        // true only for TRUE. Direct unboxing (boolean x = hasKey(...)) would NPE on null.
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Returns the list of the calling user's accepted friends who are currently
     * online, enriched with their usernames fetched from the identity service.
     *
     * Flow:
     *   1. Load all accepted friend IDs from Postgres (already indexed).
     *   2. Filter to those whose presence key exists in Redis.
     *   3. Enrich each online friend with a UserSummary from the sso-service.
     */
    public List<FriendPresenceResponse> getOnlineFriends(Long userId) {
        List<Long> friendIds = friendshipRepository.findFriendIds(userId);

        if (friendIds.isEmpty()) {
            log.debug("User {} has no accepted friends — returning empty presence list", userId);
            return List.of();
        }

        List<FriendPresenceResponse> onlineFriends = friendIds.stream()
                .filter(this::isOnline)
                .map(friendId -> {
                    UserSummary summary = userServiceClient.getUserSummary(friendId).getData();
                    return FriendPresenceResponse.builder()
                            .userId(friendId)
                            .username(summary != null ? summary.getUsername() : null)
                            .online(true)
                            .build();
                })
                .collect(Collectors.toList());

        log.debug("User {} has {}/{} friends online", userId, onlineFriends.size(), friendIds.size());
        return onlineFriends;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

}