package chat_application.example.chat_application.controller;

import chat_application.example.chat_application.dto.DisconnectResultDTO;
import chat_application.example.chat_application.dto.response.ChatSessionResponseDTO;
import chat_application.example.chat_application.dto.response.OnlineUsersResponseDTO;
import chat_application.example.chat_application.service.SessionService;
import chat_application.example.chat_application.utill.ApiResponse;
import chat_application.example.chat_application.utill.RedisUtill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    private final SessionService sessionService;
    private final RedisUtill redisUtill;

    @PostMapping("/connect")
    public ResponseEntity<ApiResponse<ChatSessionResponseDTO>> connect(@RequestParam Long userId,
                                                                       @RequestParam(required = false) Long roomId,
                                                                       @RequestParam(required = false) String websocketId){

        log.info("Connecting user with ID: {} to room ID: {} using websocket ID: {}", userId, roomId, websocketId);

        ChatSessionResponseDTO session = sessionService.connect(userId, roomId, websocketId);

        if (roomId != null) {
            sessionService.broadcastUserJoined(userId, roomId);
        }

        return ResponseEntity.ok(ApiResponse.success("Connected successfully", session));
    }

    @PostMapping("/disconnect/{roomId}")
    public ResponseEntity<ApiResponse<Void>> closeChatWindow(@RequestParam Long userId, @PathVariable Long roomId) {

        log.info("disconnect request - userId: {}, roomId: {}", userId, roomId);

        DisconnectResultDTO disconnectResultDTO = sessionService.disconnect(userId, roomId);

        if(disconnectResultDTO != null){

            for (Long sessionId : disconnectResultDTO.getSessionIdsToDelete()) {
                redisUtill.deleteSessionFromRedis(sessionId);
            }

            for(Long rid : disconnectResultDTO.getRoomIds()){
                sessionService.broadcastUserLeft(disconnectResultDTO.getUser().getId(), rid);
            }

        }

        return ResponseEntity.ok(ApiResponse.success("disconnect", null));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<ApiResponse<Void>> disconnect(@RequestParam Long userId) {

        log.info("Disconnect all request - userId: {}", userId);

        DisconnectResultDTO disconnectResultDTO = sessionService.disconnect(userId, null);  // null roomId = disconnect all

        if(disconnectResultDTO != null){

            for (Long sessionId : disconnectResultDTO.getSessionIdsToDelete()) {
                redisUtill.deleteSessionFromRedis(sessionId);
            }

            for(Long rid : disconnectResultDTO.getRoomIds()){
                sessionService.broadcastUserLeft(disconnectResultDTO.getUser().getId(), rid);
            }

        }
        for(Long rid : disconnectResultDTO.getRoomIds()){
            sessionService.broadcastUserLeft(disconnectResultDTO.getUser().getId(), rid);
        }

        return ResponseEntity.ok(ApiResponse.success("Disconnected successfully", null));
    }

    @GetMapping("/rooms/{roomId}/online")
    public ResponseEntity<ApiResponse<OnlineUsersResponseDTO>> getOnlineUsers(@PathVariable Long roomId) {

        OnlineUsersResponseDTO onlineUsers = sessionService.getOnlineUsers(roomId);

        return ResponseEntity.ok(ApiResponse.success("Online users retrieved", onlineUsers));
    }

    @GetMapping("/users/{userId}/online")
    public ResponseEntity<ApiResponse<Boolean>> isUserOnline(@PathVariable Long userId) {

        boolean isOnline = sessionService.isUserOnline(userId);
        String message = isOnline ? "User is online" : "User is offline";

        return ResponseEntity.ok(ApiResponse.success(message, isOnline));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<String>> heartbeat(@RequestParam Long userId,@RequestParam Long roomId){

        log.debug("Heartbeat received - User: {}, Room: {}", userId, roomId);

        redisUtill.refreshSessionTtlForUser(userId, roomId);

        return ResponseEntity.ok(ApiResponse.success("Session TTL refreshed", "OK"));

    }
}
