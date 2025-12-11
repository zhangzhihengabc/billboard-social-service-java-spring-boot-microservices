package chat_application.example.chat_application.websocket;

import chat_application.example.chat_application.entities.chatEntities.ChatSession;
import chat_application.example.chat_application.entities.enums.PresenceStatus;
import chat_application.example.chat_application.repository.chatSessionRepository;
import chat_application.example.chat_application.service.sessionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class redisKeyExpirationListener implements MessageListener {

    private final chatSessionRepository chatSessionRepository;
    private final sessionService sessionService;

    private static final String SESSION_KEY_PREFIX = "chat:session:";

    @Override
    @Transactional
    public void onMessage(Message message, byte @Nullable [] pattern) {

        String expiredKey = new String(message.getBody());

        if (!expiredKey.startsWith(SESSION_KEY_PREFIX)) {
            return;
        }

        try {
            String sessionIdStr = expiredKey.substring(SESSION_KEY_PREFIX.length());
            Long sessionId = Long.parseLong(sessionIdStr);

            chatSessionRepository.findById(sessionId).ifPresent(session -> {
                if (session.getIsActive()) {
                    handleSessionExpiration(session);
                }
            });

        } catch (NumberFormatException e) {
            log.warn("Could not parse session ID from expired key: {}", expiredKey);
        } catch (Exception e) {
            log.error("Error handling session expiration for key {}: {}", expiredKey, e.getMessage());
        }
    }

    private void handleSessionExpiration(ChatSession session) {
        Long userId = session.getUser().getId();
        Long roomId = session.getRoom() != null ? session.getRoom().getId() : null;

        log.info("Session {} expired for user {} in room {}",
                session.getId(), userId, roomId);

        session.disconnect();
        chatSessionRepository.save(session);

        if (roomId != null) {
            sessionService.broadcastPresence(roomId, session.getUser(), "TIMEOUT", PresenceStatus.OFFLINE);
        }

        log.info("User {} marked offline due to session timeout", userId);
    }
}
