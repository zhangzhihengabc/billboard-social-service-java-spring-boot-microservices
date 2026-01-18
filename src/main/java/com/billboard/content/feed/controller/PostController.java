package com.billboard.content.feed.controller;

import com.billboard.content.feed.dto.request.FeedRequests.*;
import com.billboard.content.feed.dto.response.FeedResponses.*;
import com.billboard.content.security.UserPrincipal;
import com.billboard.content.feed.service.PostService;
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
@Tag(name = "Posts", description = "Post management endpoints")
public class PostController {

    private final PostService postService;

    @PostMapping
    @Operation(summary = "Create a new post")
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

    @PostMapping("/share")
    @Operation(summary = "Share a post")
    public ResponseEntity<PostResponse> sharePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SharePostRequest request) {
        PostResponse response = postService.sharePost(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{postId}/pin")
    @Operation(summary = "Pin post to wall")
    public ResponseEntity<Void> pinPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId) {
        postService.pinPost(principal.getId(), postId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{postId}/pin")
    @Operation(summary = "Unpin post from wall")
    public ResponseEntity<Void> unpinPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId) {
        postService.unpinPost(principal.getId(), postId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search posts")
    public ResponseEntity<Page<PostResponse>> searchPosts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PostResponse> response = postService.searchPosts(query, page, size);
        return ResponseEntity.ok(response);
    }
}
