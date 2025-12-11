package chat_application.example.chat_application.websocket;

import chat_application.example.chat_application.dto.ErrorDTO;
import chat_application.example.chat_application.dto.event.readEventDTO;
import chat_application.example.chat_application.dto.request.SendMessageRequestDTO;
import chat_application.example.chat_application.dto.request.readRequestDTO;
import chat_application.example.chat_application.dto.request.typingRequestDTO;
import chat_application.example.chat_application.dto.response.messageResponseDTO;
import chat_application.example.chat_application.dto.event.typingEventDTO;
import chat_application.example.chat_application.dto.userSummaryDTO;
import chat_application.example.chat_application.service.messageService;
import chat_application.example.chat_application.service.readService;
import chat_application.example.chat_application.utill.redisUtill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketMessageEventListener {

    private final messageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final redisUtill redisUtill;
    private final readService readService;

    /**
     * Send error message to specific user
     */
    private void sendErrorToUser(Long userId, String errorMessage) {
        try {
            ErrorDTO error = new ErrorDTO("MESSAGE_SEND_FAILED", errorMessage, LocalDateTime.now());
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/errors",
                    error
            );
        } catch (Exception e) {
            log.error("Failed to send error to user: {}", e.getMessage());
        }
    }

    @MessageMapping("/room/{roomId}/send")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Payload SendMessageRequestDTO request) {

        log.info("WebSocket: User {} sending message to room {}", request.getSenderId(), roomId);

        request.setRoomId(roomId);

        try {
            // 1. Save message (transaction commits when method returns)
            messageResponseDTO savedMessage = messageService.saveMessage(request);

            // 2. Broadcast AFTER transaction commits
            messageService.broadcastMessage(roomId, savedMessage);

            log.info("WebSocket: Message {} sent to room {}", savedMessage.getId(), roomId);

        } catch (Exception e) {
            log.error("WebSocket: Failed to send message: {}", e.getMessage(), e);

            // Send error back to sender
            sendErrorToUser(request.getSenderId(), "Failed to send message: " + e.getMessage());
        }
    }

    @MessageMapping("/room/{roomId}/typing")
    public void handleTyping(
            @DestinationVariable Long roomId,
            @Payload typingRequestDTO request) {

        log.info("WebSocket: User {} {} in room {}",
                request.getUserId(),
                request.getIsTyping() ? "is typing" : "stopped typing",
                roomId);

        userSummaryDTO user = userSummaryDTO.builder()
                .id(request.getUserId())
                .username(request.getUsername())
                .isOnline(true)
                .build();

        typingEventDTO typingEvent = typingEventDTO.builder()
                .roomId(roomId)
                .user(user)
                .isTyping(request.getIsTyping())
                .timestamp(LocalDateTime.now())
                .build();

        // Broadcast typing indicator
        String destination = "/topic/room/" + roomId + "/typing";
        messagingTemplate.convertAndSend(destination, typingEvent);

        // Refresh TTL if user is typing (user is active)
        if (Boolean.TRUE.equals(request.getIsTyping())) {
            redisUtill.refreshSessionTtlForUser(request.getUserId(), roomId);
        }
    }

    @MessageMapping("/room/{roomId}/read")
    public void handleRead(
            @DestinationVariable Long roomId,
            @Payload readRequestDTO request) {

        log.info("WebSocket: User {} read up to message {} in room {}",
                request.getUserId(),
                request.getLastReadMessageId(),
                roomId);

        try {
            // 1. Update lastReadMessageId in database (transaction)
            readService.markMessagesAsRead(request.getUserId(), roomId, request.getLastReadMessageId());

            // 2. Build read event
            readEventDTO readEvent = readEventDTO.builder()
                    .roomId(roomId)
                    .userId(request.getUserId())
                    .username(request.getUsername())
                    .lastReadMessageId(request.getLastReadMessageId())
                    .messageIds(request.getMessageIds())
                    .readAt(LocalDateTime.now())
                    .build();

            // 3. Broadcast read receipt to room (AFTER transaction)
            String destination = "/topic/room/" + roomId + "/read";
            messagingTemplate.convertAndSend(destination, readEvent);

            // 4. Refresh TTL (user is active)
            redisUtill.refreshSessionTtlForUser(request.getUserId(), roomId);

            log.debug("WebSocket: Read receipt broadcast to {}", destination);

        } catch (Exception e) {
            log.error("WebSocket: Failed to process read: {}", e.getMessage(), e);
        }
    }



}
