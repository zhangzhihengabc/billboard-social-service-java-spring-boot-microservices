package chat_application.example.chat_application.controller;

import chat_application.example.chat_application.dto.event.RealTimeCommentsStatusDTO;
import chat_application.example.chat_application.dto.request.RealTimeStatusRequestDTO;
import chat_application.example.chat_application.dto.request.comment.*;
import chat_application.example.chat_application.dto.response.CommentResponseDTO;
import chat_application.example.chat_application.service.CommentService;
import chat_application.example.chat_application.utill.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> addPostComment(
            @RequestParam Long userId,
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequestDTO request) {

        CommentResponseDTO comment = commentService.addComment(postId, userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Comment added successfully", comment));
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<Page<CommentResponseDTO>>> getPostComments(
            @RequestParam Long userId,
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<CommentResponseDTO> comments = commentService.getComments("post", postId, userId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));
    }

    @PostMapping("/comments/entity")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> addEntityComment(
            @RequestParam Long userId,
            @Valid @RequestBody EntityCommentRequestDTO request) {

        CommentResponseDTO comment = commentService.addEntityComment(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Comment added successfully", comment));
    }

    @GetMapping("/comments/entity/{entityType}/{entityId}")
    public ResponseEntity<ApiResponse<Page<CommentResponseDTO>>> getEntityComments(
            @RequestParam Long userId,
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<CommentResponseDTO> comments = commentService.getComments(entityType, entityId, userId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));
    }

    @PostMapping("/comments/object")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> addObjectComment(
            @RequestParam Long userId,
            @Valid @RequestBody ObjectCommentRequestDTO request) {

        CommentResponseDTO comment = commentService.addObjectComment(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Comment added successfully", comment));
    }

    @GetMapping("/comments/object/{entityType}/{entityId}/{objectType}/{objectId}")
    public ResponseEntity<ApiResponse<Page<CommentResponseDTO>>> getObjectComments(
            @RequestParam Long userId,
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PathVariable String objectType,
            @PathVariable Long objectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<CommentResponseDTO> comments = commentService.getObjectComments(
                entityType, entityId, objectType, objectId, userId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));
    }

    @GetMapping("/videos/{videoId}/comments/timestamp")
    public ResponseEntity<ApiResponse<List<CommentResponseDTO>>> getVideoCommentsByTime(
            @RequestParam Long userId,
            @PathVariable Long videoId,
            @RequestParam Integer startTime,
            @RequestParam Integer endTime) {

        List<CommentResponseDTO> comments = commentService.getVideoCommentsByTime(videoId, startTime, endTime, userId);

        return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));
    }

    @GetMapping("/replays/{replayId}/comments/timestamp")
    public ResponseEntity<ApiResponse<List<CommentResponseDTO>>> getReplayCommentsByTime(
            @RequestParam Long userId,
            @PathVariable Long replayId,
            @RequestParam Integer startTime,
            @RequestParam Integer endTime) {

        List<CommentResponseDTO> comments = commentService.getReplayCommentsByTime(replayId, startTime, endTime, userId);

        return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));
    }


    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> editComment(
            @RequestParam Long userId,
            @PathVariable Long commentId,
            @Valid @RequestBody EditCommentRequestDTO request) {

        CommentResponseDTO comment = commentService.editComment(commentId, userId, request);

        return ResponseEntity.ok(ApiResponse.success("Comment edited successfully", comment));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @RequestParam Long userId,
            @PathVariable Long commentId) {

        commentService.deleteComment(commentId, userId);

        return ResponseEntity.ok(ApiResponse.success("Comment deleted successfully", null));
    }

    @PostMapping("/comments/{commentId}/embed")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> addEmbed(
            @RequestParam Long userId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentEmbedRequestDTO request) {

        CommentResponseDTO comment = commentService.addEmbed(commentId, userId, request);

        return ResponseEntity.ok(ApiResponse.success("Embed added successfully", comment));
    }


    @PostMapping("/comments/{commentId}/attachment")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> addAttachment(
            @RequestParam Long userId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentAttachmentRequestDTO request) {

        CommentResponseDTO comment = commentService.addAttachment(commentId, userId, request);

        return ResponseEntity.ok(ApiResponse.success("Attachment added successfully", comment));
    }

    /**
     * Get comment replies
     */
    @GetMapping("/comments/{commentId}/replies")
    public ResponseEntity<ApiResponse<Page<CommentResponseDTO>>> getReplies(
            @RequestParam Long userId,
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

//        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        Pageable pageable = PageRequest.of(page, size);
        Page<CommentResponseDTO> replies = commentService.getReplies(commentId, userId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Replies retrieved", replies));
    }


    /**
     * Pin/unpin comment
     */
    @PostMapping("/comments/{commentId}/pin")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> togglePin(
            @RequestParam Long userId,
            @PathVariable Long commentId) {

        CommentResponseDTO comment = commentService.togglePin(commentId, userId);

        return ResponseEntity.ok(ApiResponse.success("Pin toggled successfully", comment));
    }

    /**
     * Highlight comment (creator heart)
     */
    @PostMapping("/comments/{commentId}/highlight")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> toggleHighlight(
            @RequestParam Long userId,
            @PathVariable Long commentId) {

        CommentResponseDTO comment = commentService.toggleHighlight(commentId, userId);

        return ResponseEntity.ok(ApiResponse.success("Highlight toggled successfully", comment));
    }

    /**
     * Get pinned comments
     */
    @GetMapping("/comments/entity/{entityType}/{entityId}/pinned")
    public ResponseEntity<ApiResponse<List<CommentResponseDTO>>> getPinnedComments(
            @RequestParam Long userId,
            @PathVariable String entityType,
            @PathVariable Long entityId) {

//        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        List<CommentResponseDTO> comments = commentService.getPinnedComments(entityType, entityId, userId);

        return ResponseEntity.ok(ApiResponse.success("Pinned comments retrieved", comments));
    }

    @GetMapping("/comments/realtime/status")
    public ResponseEntity<ApiResponse<RealTimeCommentsStatusDTO>> getRealtimeStatus(
            @RequestParam Long userId,
            @RequestParam String entityType,
            @RequestParam Long entityId,
            @RequestParam(required = false) Long afterCommentId) {

        RealTimeCommentsStatusDTO status = commentService.getRealtimeStatus(entityType, entityId, userId, afterCommentId);

        return ResponseEntity.ok(ApiResponse.success("Status retrieved", status));
    }

    @PostMapping("/comments/realtime/status")
    public ResponseEntity<ApiResponse<Void>> setRealtimeStatus(
            @RequestParam Long userId,
            @Valid @RequestBody RealTimeStatusRequestDTO request) {

        commentService.setRealtimeStatus(userId, request);

        return ResponseEntity.ok(ApiResponse.success("Status updated", null));
    }
}
