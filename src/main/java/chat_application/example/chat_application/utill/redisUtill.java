package chat_application.example.chat_application.utill;

import chat_application.example.chat_application.repository.chatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class redisUtill {

    private final RedisTemplate<String, String> redisTemplate;
    private final chatSessionRepository chatSessionRepository;
    private static final String SESSION_KEY_PREFIX = "chat:session:";

    @Value("${chat.session.ttl-seconds}")
    private Long sessionTtlSeconds;

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
