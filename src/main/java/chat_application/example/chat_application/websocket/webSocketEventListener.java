package chat_application.example.chat_application.websocket;

import chat_application.example.chat_application.dto.disconnectResultDTO;
import chat_application.example.chat_application.service.sessionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
@Slf4j
public class webSocketEventListener {

    private final sessionService sessionService;

    /**
     * Handle WebSocket Connect Event
     *
     * Called when a new WebSocket STOMP connection is established.
     * At this point, the client has successfully connected but hasn't
     * subscribed to any destinations yet.
     *
     * @param event - Contains session information and headers
     */
    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        // Wrap the message to access STOMP headers easily
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();

        log.info("New WebSocket connection established. Session ID: {}, User: {}", sessionId, user != null ? user.getName() : "Anonymous");

    }

    /**
     * Handle WebSocket Disconnect Event
     *
     * Called when a WebSocket connection is closed, either:
     * - Client explicitly disconnects
     * - Network failure
     * - Server closes the connection
     *
     * IMPORTANT: Always clean up resources here to prevent memory leaks!
     *
     * @param event - Contains session information
     */
    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();

        log.info("WebSocket disconnected. Session ID: {}, User: {}",
                sessionId, user != null ? user.getName() : "Anonymous");

        if (user != null) {
            try {
                Long userId = Long.parseLong(user.getName());

                disconnectResultDTO result = sessionService.handleWebSocketDisconnect(userId, sessionId);

                if(result != null){
                    for(Long rid : result.getRoomIds()){
                        sessionService.broadcastUserLeft(result.getUser().getId(), rid);
                    }
                }

            } catch (NumberFormatException e) {
                log.warn("Could not parse user ID from principal: {}", user.getName());
            }
        }
    }

    /**
     * Handle Subscribe Event
     *
     * Called when a client subscribes to a destination (topic/queue).
     * Useful for:
     * - Tracking which rooms/channels a user is watching
     * - Authorization checks before allowing subscription
     * - Sending initial state when user joins
     *
     * @param event - Contains subscription destination
     */
    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String destination = accessor.getDestination();
        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();

        log.info("User subscribed. Session ID: {}, User: {}, Destination: {}", sessionId, user != null ? user.getName() : "Anonymous", destination);

    }

    @PostConstruct
    public void init() {
        log.info("✅ WebSocketEventListener initialized!");
    }
}
