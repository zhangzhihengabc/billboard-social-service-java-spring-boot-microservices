package chat_application.example.chat_application.utill;

import chat_application.example.chat_application.repository.chatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class redisUtill {

    private final RedisTemplate<String, String> redisTemplate;
    private final chatSessionRepository chatSessionRepository;

    // Chat session keys
    private static final String SESSION_KEY_PREFIX = "chat:session:";
    private static final String TYPING_KEY_PREFIX = "chat:typing:";
    private static final String PRESENCE_KEY_PREFIX = "chat:presence:";
    private static final String ROOM_USERS_KEY_PREFIX = "chat:room:users:";

    // Comment real-time keys
    private static final String COMMENT_REALTIME_KEY_PREFIX = "comment:realtime:";
    private static final String COMMENT_TYPING_KEY_PREFIX = "comment:typing:";
    private static final String COMMENT_TYPING_USER_KEY_PREFIX = "comment:typing:user:";

    // Generic keys
    private static final String CACHE_KEY_PREFIX = "cache:";

    @Value("${chat.session.ttl-seconds}")
    private Long sessionTtlSeconds;

    public Set<String> getCommentTypingUsers(String entityType, Long entityId) {
        String typingSetKey = COMMENT_TYPING_KEY_PREFIX + entityType + ":" + entityId;
        return redisTemplate.opsForSet().members(typingSetKey);
    }

    public String getCommentTypingStartTime(String entityType, Long entityId, Long userId) {
        String typingUserKey = COMMENT_TYPING_USER_KEY_PREFIX + entityType + ":" + entityId + ":" + userId;
        return redisTemplate.opsForValue().get(typingUserKey);
    }

    public boolean isCommentRealtimeEnabled(String entityType, Long entityId, Long userId) {
        String key = COMMENT_REALTIME_KEY_PREFIX + entityType + ":" + entityId + ":" + userId;
        String value = redisTemplate.opsForValue().get(key);
        return "true".equalsIgnoreCase(value);
    }

    public void enableCommentRealtime(String entityType, Long entityId, Long userId, long ttlMinutes) {
        String key = COMMENT_REALTIME_KEY_PREFIX + entityType + ":" + entityId + ":" + userId;
        redisTemplate.opsForValue().set(key, "true", ttlMinutes, TimeUnit.MINUTES);
        log.debug("Real-time comments enabled for user {} on {} {}", userId, entityType, entityId);
    }

    public void disableCommentRealtime(String entityType, Long entityId, Long userId) {
        String key = COMMENT_REALTIME_KEY_PREFIX + entityType + ":" + entityId + ":" + userId;
        redisTemplate.delete(key);
        log.debug("Real-time comments disabled for user {} on {} {}", userId, entityType, entityId);
    }

    public void setCommentTyping(String entityType, Long entityId, Long userId, boolean isTyping) {
        String typingSetKey = COMMENT_TYPING_KEY_PREFIX + entityType + ":" + entityId;
        String typingUserKey = COMMENT_TYPING_USER_KEY_PREFIX + entityType + ":" + entityId + ":" + userId;

        if (isTyping) {
            redisTemplate.opsForSet().add(typingSetKey, userId.toString());
            redisTemplate.expire(typingSetKey, 30, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(typingUserKey, LocalDateTime.now().toString(), 10, TimeUnit.SECONDS);
            log.debug("Comment typing started for user {} on {} {}", userId, entityType, entityId);
        } else {
            redisTemplate.opsForSet().remove(typingSetKey, userId.toString());
            redisTemplate.delete(typingUserKey);
            log.debug("Comment typing stopped for user {} on {} {}", userId, entityType, entityId);
        }
    }

    public void refreshSessionTtl(Long sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        Boolean success = redisTemplate.expire(key, Duration.ofSeconds(sessionTtlSeconds));

        if (Boolean.TRUE.equals(success)) {
            log.debug("Redis TTL refreshed for session {}", sessionId);
        } else {
            log.warn("Could not refresh TTL for session {} - key may not exist", sessionId);
        }
    }

    public void storeSessionInRedis(Long sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, String.valueOf(sessionId), Duration.ofSeconds(sessionTtlSeconds));
        log.debug("Redis SET {} with TTL {} seconds", key, sessionTtlSeconds);
    }

    public void deleteSessionFromRedis(Long sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Redis DELETE {} - success: {}", key, deleted);
    }

    public boolean isSessionActiveInRedis(Long sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void refreshSessionTtlForUser(Long userId, Long roomId) {
        chatSessionRepository.findByUserIdAndRoomIdAndIsActiveTrue(userId, roomId)
                .ifPresent(session -> {
                    // Refresh Redis TTL
                    refreshSessionTtl(session.getId());

                    session.updateActivity();
                    chatSessionRepository.save(session);

                    log.debug("TTL refreshed for user {} in room {} (session {})",
                            userId, roomId, session.getId());
                });
    }
}
