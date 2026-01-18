package com.billboard.content.forum.controller;

import com.billboard.content.forum.dto.request.ForumRequests.*;
import com.billboard.content.forum.dto.response.ForumResponses.*;
import com.billboard.content.security.UserPrincipal;
import com.billboard.content.forum.service.PostService;
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
@RequestMapping("/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Post/Reply management")
public class PostController {

    private final PostService postService;

    @PostMapping
    @Operation(summary = "Create post/reply")
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePostRequest request) {
        PostResponse response = postService.createPost(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{postId}")
    @Operation(summary = "Get post by ID")
    public ResponseEntity<PostResponse> getPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId) {
        UUID userId = principal != null ? principal.getId() : null;
        PostResponse response = postService.getPost(postId, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{postId}")
    @Operation(summary = "Update post")
    public ResponseEntity<PostResponse> updatePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @Valid @RequestBody UpdatePostRequest request) {
        PostResponse response = postService.updatePost(principal.getId(), postId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "Delete post")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId) {
        postService.deletePost(principal.getId(), postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{postId}/vote")
    @Operation(summary = "Vote on post")
    public ResponseEntity<Void> votePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @Valid @RequestBody VoteRequest request) {
        postService.votePost(principal.getId(), postId, request.getVoteType());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{postId}/solution")
    @Operation(summary = "Mark as solution")
    public ResponseEntity<PostResponse> markAsSolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId) {
        PostResponse response = postService.markAsSolution(principal.getId(), postId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/topic/{topicId}")
    @Operation(summary = "Get topic posts")
    public ResponseEntity<Page<PostResponse>> getTopicPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID topicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID userId = principal != null ? principal.getId() : null;
        Page<PostResponse> response = postService.getTopicPosts(topicId, userId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user's posts")
    public ResponseEntity<Page<PostResponse>> getUserPosts(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<PostResponse> response = postService.getUserPosts(userId, page, size);
        return ResponseEntity.ok(response);
    }
}
