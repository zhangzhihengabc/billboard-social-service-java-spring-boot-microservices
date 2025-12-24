package com.ossn.content.feed.controller;

import com.ossn.content.feed.dto.response.FeedResponses.*;
import com.ossn.content.security.UserPrincipal;
import com.ossn.content.feed.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/feed")
@RequiredArgsConstructor
@Tag(name = "Feed", description = "News feed endpoints")
public class FeedController {

    private final PostService postService;

    @GetMapping
    @Operation(summary = "Get user's personalized feed")
    public ResponseEntity<Page<PostResponse>> getUserFeed(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PostResponse> response = postService.getUserFeed(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/public")
    @Operation(summary = "Get public feed")
    public ResponseEntity<Page<PostResponse>> getPublicFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PostResponse> response = postService.getPublicFeed(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/trending")
    @Operation(summary = "Get trending posts")
    public ResponseEntity<Page<PostResponse>> getTrendingPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PostResponse> response = postService.getTrendingPosts(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user's posts")
    public ResponseEntity<Page<PostResponse>> getUserPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = principal != null ? principal.getId() : null;
        Page<PostResponse> response = postService.getUserPosts(userId, currentUserId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/wall/{userId}")
    @Operation(summary = "Get wall posts")
    public ResponseEntity<Page<PostResponse>> getWallPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = principal != null ? principal.getId() : null;
        Page<PostResponse> response = postService.getWallPosts(userId, currentUserId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/group/{groupId}")
    @Operation(summary = "Get group posts")
    public ResponseEntity<Page<PostResponse>> getGroupPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = principal != null ? principal.getId() : null;
        Page<PostResponse> response = postService.getGroupPosts(groupId, currentUserId, page, size);
        return ResponseEntity.ok(response);
    }
}
