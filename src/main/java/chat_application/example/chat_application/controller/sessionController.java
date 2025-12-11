package chat_application.example.chat_application.controller;

import chat_application.example.chat_application.dto.*;
import chat_application.example.chat_application.dto.response.chatSessionResponseDTO;
import chat_application.example.chat_application.dto.response.onlineUsersResponseDTO;
import chat_application.example.chat_application.utill.redisUtill;
import chat_application.example.chat_application.service.sessionService;
import chat_application.example.chat_application.utill.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class sessionController {

    private final sessionService sessionService;
    private final redisUtill redisUtill;

    @PostMapping("/connect")
    public ResponseEntity<ApiResponse<chatSessionResponseDTO>> connect(@RequestParam Long userId,
                                                                       @RequestParam(required = false) Long roomId,
                                                                       @RequestParam(required = false) String websocketId){

        log.info("Connecting user with ID: {} to room ID: {} using websocket ID: {}", userId, roomId, websocketId);

        chatSessionResponseDTO session = sessionService.connect(userId, roomId, websocketId);

        if (roomId != null) {
            sessionService.broadcastUserJoined(userId, roomId);
        }

        return ResponseEntity.ok(ApiResponse.success("Connected successfully", session));
    }

    @PostMapping("/disconnect/{roomId}")
    public ResponseEntity<ApiResponse<Void>> closeChatWindow(@RequestParam Long userId, @PathVariable Long roomId) {

        log.info("disconnect request - userId: {}, roomId: {}", userId, roomId);

        disconnectResultDTO disconnectResultDTO = sessionService.disconnect(userId, roomId);

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

        disconnectResultDTO disconnectResultDTO = sessionService.disconnect(userId, null);  // null roomId = disconnect all

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
    public ResponseEntity<ApiResponse<onlineUsersResponseDTO>> getOnlineUsers(@PathVariable Long roomId) {

        onlineUsersResponseDTO onlineUsers = sessionService.getOnlineUsers(roomId);

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
