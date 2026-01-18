package com.billboard.content.feed.controller;

import com.billboard.content.feed.dto.request.FeedRequests.*;
import com.billboard.content.feed.dto.response.FeedResponses.*;
import com.billboard.content.security.UserPrincipal;
import com.billboard.content.feed.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/posts/{postId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Comment management endpoints")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @Operation(summary = "Create a comment")
    public ResponseEntity<CommentResponse> createComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = commentService.createComment(principal.getId(), postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get post comments")
    public ResponseEntity<Page<CommentResponse>> getPostComments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = principal != null ? principal.getId() : null;
        Page<CommentResponse> response = commentService.getPostComments(postId, userId, page, size);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "Update comment")
    public ResponseEntity<CommentResponse> updateComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        CommentResponse response = commentService.updateComment(principal.getId(), commentId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete comment")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        commentService.deleteComment(principal.getId(), commentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{commentId}/replies")
    @Operation(summary = "Get comment replies")
    public ResponseEntity<Page<CommentResponse>> getCommentReplies(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = principal != null ? principal.getId() : null;
        Page<CommentResponse> response = commentService.getCommentReplies(commentId, userId, page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{commentId}/like")
    @Operation(summary = "Like comment")
    public ResponseEntity<Void> likeComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        commentService.likeComment(principal.getId(), commentId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{commentId}/like")
    @Operation(summary = "Unlike comment")
    public ResponseEntity<Void> unlikeComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        commentService.unlikeComment(principal.getId(), commentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{commentId}/pin")
    @Operation(summary = "Pin comment")
    public ResponseEntity<Void> pinComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        commentService.pinComment(principal.getId(), postId, commentId);
        return ResponseEntity.ok().build();
    }
}
