package chat_application.example.chat_application.controller;

import chat_application.example.chat_application.dto.messageReadInfoDTO;
import chat_application.example.chat_application.dto.request.editMessageRequestDTO;
import chat_application.example.chat_application.dto.response.messageResponseDTO;
import chat_application.example.chat_application.service.messageService;
import chat_application.example.chat_application.utill.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Slf4j
public class messageController {

    private final messageService messageService;

    /**
     * Get messages for a room (paginated, newest first)
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<ApiResponse<Page<messageResponseDTO>>> getMessages(
            @PathVariable Long roomId,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("Getting messages for room {} by user {}", roomId, userId);

        Page<messageResponseDTO> messages = messageService.getMessages(roomId, userId, page, size);

        return ResponseEntity.ok(ApiResponse.success("Messages retrieved", messages));
    }

    /**
     * Get new messages after a specific message ID
     */
    @GetMapping("/room/{roomId}/new")
    public ResponseEntity<ApiResponse<List<messageResponseDTO>>> getNewMessages(
            @PathVariable Long roomId,
            @RequestParam Long userId,
            @RequestParam Long lastMessageId) {

        log.info("Getting new messages for room {} after message {}", roomId, lastMessageId);

        List<messageResponseDTO> messages = messageService.getNewMessages(roomId, userId, lastMessageId);

        return ResponseEntity.ok(ApiResponse.success("New messages retrieved", messages));
    }

    /**
     * Edit a message
     */
    @PutMapping("/{messageId}")
    public ResponseEntity<ApiResponse<messageResponseDTO>> editMessage(
            @PathVariable Long messageId,
            @RequestParam Long userId,
            @RequestBody editMessageRequestDTO request) {

        log.info("User {} editing message {}", userId, messageId);

        messageResponseDTO message = messageService.editMessage(messageId, userId, request.getContent());

        messageService.broadcastMessage(message.getRoomId(), message);

        return ResponseEntity.ok(ApiResponse.success("Message edited", message));
    }

    /**
     * Delete a message
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResponse<messageResponseDTO>> deleteMessage(
            @PathVariable Long messageId,
            @RequestParam Long userId) {

        log.info("User {} deleting message {}", userId, messageId);

        messageResponseDTO message = messageService.deleteMessage(messageId, userId);

        messageService.broadcastMessage(message.getRoomId(), message);

        return ResponseEntity.ok(ApiResponse.success("Message deleted", message));
    }

    /**
     * Pin a message
     */
    @PostMapping("/{messageId}/pin")
    public ResponseEntity<ApiResponse<messageResponseDTO>> pinMessage(
            @PathVariable Long messageId,
            @RequestParam Long userId,
            @RequestParam Long roomId) {

        log.info("User {} pinning message {} in room {}", userId, messageId, roomId);

        messageResponseDTO message = messageService.pinMessage(messageId, userId, roomId);

        messageService.broadcastMessage(message.getRoomId(), message);

        return ResponseEntity.ok(ApiResponse.success("Message pinned", message));
    }

    /**
     * Unpin a message
     */
    @PostMapping("/{messageId}/unpin")
    public ResponseEntity<ApiResponse<messageResponseDTO>> unpinMessage(
            @PathVariable Long messageId,
            @RequestParam Long userId,
            @RequestParam Long roomId) {

        log.info("User {} unpinning message {} in room {}", userId, messageId, roomId);

        messageResponseDTO message = messageService.unpinMessage(messageId, userId, roomId);

        messageService.broadcastMessage(message.getRoomId(), message);

        return ResponseEntity.ok(ApiResponse.success("Message unpinned", message));
    }

    /**
     * Like a message
     */
    @PostMapping("/{messageId}/like")
    public ResponseEntity<ApiResponse<messageResponseDTO>> likeMessage(
            @PathVariable Long messageId,
            @RequestParam Long userId) {

        log.info("User {} liking message {}", userId, messageId);

        messageResponseDTO message = messageService.likeMessage(messageId, userId);

        messageService.broadcastMessage(message.getRoomId(), message);

        return ResponseEntity.ok(ApiResponse.success("Message liked", message));
    }

    /**
     * Unlike a message
     */
    @PostMapping("/{messageId}/unlike")
    public ResponseEntity<ApiResponse<messageResponseDTO>> unlikeMessage(
            @PathVariable Long messageId,
            @RequestParam Long userId) {

        log.info("User {} unliking message {}", userId, messageId);

        messageResponseDTO message = messageService.unlikeMessage(messageId, userId);

        messageService.broadcastMessage(message.getRoomId(), message);

        return ResponseEntity.ok(ApiResponse.success("Message unliked", message));
    }

    /**
     * GET /api/v1/messages/{messageId}/info
     * Get read info - who viewed and who didn't view this message
     */
    @GetMapping("/{messageId}/info")
    public ResponseEntity<ApiResponse<messageReadInfoDTO>> getMessageReadInfo(
            @PathVariable Long messageId,
            @RequestParam Long userId) {

        log.info("Getting read info for message {} by user {}", messageId, userId);

        messageReadInfoDTO readInfo = messageService.getMessageReadInfo(messageId, userId);

        return  ResponseEntity.ok(ApiResponse.success("Message read info retrieved", readInfo));
    }

}
