package chat_application.example.chat_application.service;

import chat_application.example.chat_application.dto.disconnectResultDTO;
import chat_application.example.chat_application.dto.event.presenceEventDTO;
import chat_application.example.chat_application.dto.response.chatSessionResponseDTO;
import chat_application.example.chat_application.dto.response.onlineUsersResponseDTO;
import chat_application.example.chat_application.dto.userSummaryDTO;
import chat_application.example.chat_application.dto.webSocketInfoDTO;
import chat_application.example.chat_application.entities.User;
import chat_application.example.chat_application.entities.chatEntities.ChatSession;
import chat_application.example.chat_application.entities.enums.PresenceStatus;
import chat_application.example.chat_application.entities.group.GroupMessageRoom;
import chat_application.example.chat_application.exception.ForbiddenException;
import chat_application.example.chat_application.exception.ResourceNotFoundException;
import chat_application.example.chat_application.repository.chatSessionRepository;
import chat_application.example.chat_application.repository.groupMessageMemberRepository;
import chat_application.example.chat_application.repository.groupMessageRoomRepository;
import chat_application.example.chat_application.repository.userRepository;
import chat_application.example.chat_application.utill.redisUtill;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class sessionService {

    private final chatSessionRepository chatSessionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final userRepository userRepository;
    private final groupMessageRoomRepository groupMessageRoomRepository;
    private final groupMessageMemberRepository groupMessageMemberRepository;
    private final redisUtill redisUtill;

    public void broadcastPresence(Long roomId, User user, String eventType, PresenceStatus status) {

        userSummaryDTO userSummary = userSummaryDTO.builder()
                .id(user.getId())
                .username(user.getName())
                .isOnline(status == PresenceStatus.ONLINE)
                .build();

        presenceEventDTO event = presenceEventDTO.builder()
                .eventType(eventType)  // JOIN, LEAVE, STATUS_CHANGE
                .roomId(roomId)
                .user(userSummary)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = "/topic/room/" + roomId + "/presence";

        try {
            messagingTemplate.convertAndSend(destination, event);
            log.debug("Broadcast {} presence event to {}", eventType, destination);
        } catch (Exception e) {
            log.warn("Failed to broadcast presence: {}", e.getMessage());
        }
    }

    private chatSessionResponseDTO buildSessionResponse(ChatSession session, Long roomId) {

        // Build WebSocket info to help client connect
        webSocketInfoDTO wsInfo = webSocketInfoDTO.builder()
                .rawEndpoint("ws://localhost:8080/ws")
                .build();

        // Add room-specific topics if connecting to a room
        if (roomId != null) {
            wsInfo.setSubscribeTopics(List.of(
                    "/topic/room/" + roomId + "/messages",    // New messages
                    "/topic/room/" + roomId + "/presence",    // User join/leave
                    "/topic/room/" + roomId + "/typing"       // Typing indicators
            ));
            wsInfo.setSendDestination("/app/room/" + roomId + "/send");
        }

        return chatSessionResponseDTO.builder()
                .id(session.getId())
                .userId(session.getUser().getId())
                .roomId(session.getRoom() != null ? session.getRoom().getId() : null)
                .sessionToken(session.getSessionToken())
                .isActive(session.getIsActive())
                .presenceStatus(session.getPresenceStatus())
                .lastActivityAt(session.getLastActivityAt())
                .connectedAt(session.getConnectedAt())
                .webSocketInfo(wsInfo)
                .build();
    }

    @Transactional
    public disconnectResultDTO handleWebSocketDisconnect(Long userId, String websocketSessionId) {

        return chatSessionRepository.findByWebsocketIdAndIsActiveTrue(websocketSessionId)
                .map(session -> {
                    Long roomId = session.getRoom() != null ? session.getRoom().getId() : null;

                    redisUtill.deleteSessionFromRedis(session.getId());

                    session.disconnect();
                    chatSessionRepository.save(session);

                    log.info("WebSocket session {} disconnected for user {}", websocketSessionId, userId);

                    return disconnectResultDTO.builder()
                            .roomIds(Collections.singletonList(roomId))
                            .user(session.getUser())  // Using DTO now
                            .build();
                })
                .orElse(null);
    }

    @Transactional
    public chatSessionResponseDTO connect(Long userId, Long roomId, String websocketId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));


        GroupMessageRoom room = null;
        if (roomId != null) {
            room = groupMessageRoomRepository.findById(roomId)
                    .orElseThrow(() -> new ResourceNotFoundException("Room", roomId));

            // Check if user is a member of this room
            if (!groupMessageMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
                log.warn("User {} is not a member of room {}", userId, roomId);
                throw new ForbiddenException("You are not a member of this room");
            }
        }

        chatSessionRepository.findByUserIdAndRoomIdAndIsActiveTrue(userId, roomId)
                .ifPresent(existingSession -> {
                    log.info("Deactivating existing session {} for user {}",
                            existingSession.getId(), userId);

                    existingSession.disconnect();
                    chatSessionRepository.save(existingSession);
                    redisUtill.deleteSessionFromRedis(existingSession.getId());
                });

        String sessionToken = UUID.randomUUID().toString();

        ChatSession session = ChatSession.builder()
                .user(user)
                .room(room)
                .sessionToken(sessionToken)
                .websocketId(websocketId)
                .presenceStatus(PresenceStatus.ONLINE)
                .lastActivityAt(LocalDateTime.now())
                .isActive(true)
                .isTyping(false)
                .build();

        session = chatSessionRepository.save(session);
        redisUtill.storeSessionInRedis(session.getId());

        log.info("Created new session {} for user {}", session.getId(), userId);

        return buildSessionResponse(session, roomId);
    }

    public void broadcastUserJoined(Long userId, Long roomId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            log.info("Broadcasting JOIN for user {} in room {}", userId, roomId);
            broadcastPresence(roomId, user, "JOIN", PresenceStatus.ONLINE);
        }
    }

    public void broadcastUserLeft(Long userId, Long roomId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            log.info("Broadcasting LEAVE for user {} in room {}", userId, roomId);
            broadcastPresence(roomId, user, "LEAVE", PresenceStatus.OFFLINE);
        }
    }

    @Transactional
    public disconnectResultDTO disconnect(Long userId, Long roomId) {

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }

        List<Long> disconnectedRoomIds = new ArrayList<>();
        List<Long> sessionIdsToDelete = new ArrayList<>();

        if (roomId != null) {
            // Disconnect from specific room
            chatSessionRepository.findByUserIdAndRoomIdAndIsActiveTrue(userId, roomId)
                    .ifPresent(session -> {
                        session.disconnect();
                        chatSessionRepository.save(session);
                        sessionIdsToDelete.add(session.getId());
                        disconnectedRoomIds.add(roomId);
                        log.info("User {} disconnected from room {}", userId, roomId);
                    });

        } else {
            // Disconnect from ALL rooms
            List<ChatSession> activeSessions = chatSessionRepository.findByUserIdAndIsActiveTrue(userId);

            for (ChatSession session : activeSessions) {
                session.disconnect();
                chatSessionRepository.save(session);
                sessionIdsToDelete.add(session.getId());
                disconnectedRoomIds.add(session.getRoom().getId());

                log.info("User {} disconnected from room {}", userId,
                        session.getRoom() != null ? session.getRoom().getId() : "global");
            }

            log.info("User {} disconnected from all {} sessions", userId, activeSessions.size());
        }

        return disconnectResultDTO.builder()
                .roomIds(disconnectedRoomIds)
                .sessionIdsToDelete(sessionIdsToDelete)
                .user(user)
                .build();
    }

    @Transactional
    public onlineUsersResponseDTO getOnlineUsers(Long roomId) {
        List<ChatSession> onlineSessions = chatSessionRepository.findOnlineUsersInRoom(roomId);

        List<userSummaryDTO> onlineUsers = onlineSessions.stream()
                .filter(session -> redisUtill.isSessionActiveInRedis(session.getId()))
                .map(session -> userSummaryDTO.builder()
                        .id(session.getUser().getId())
                        .username(session.getUser().getName())
                        .isOnline(true)
                        .build())
                .distinct()
                .toList();

        return onlineUsersResponseDTO.builder()
                .roomId(roomId)
                .onlineCount(onlineUsers.size())
                .onlineUsers(onlineUsers)
                .build();
    }

    @Transactional
    public boolean isUserOnline(Long userId) {
        List<ChatSession> sessions = chatSessionRepository.findByUserIdAndIsActiveTrue(userId);
        return sessions.stream()
                .anyMatch(session -> redisUtill.isSessionActiveInRedis(session.getId()));
    }


}
