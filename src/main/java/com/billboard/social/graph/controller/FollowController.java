package com.billboard.social.graph.controller;

import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/follows")
@RequiredArgsConstructor
@Tag(name = "Follows", description = "Follow/unfollow user management")
public class FollowController {

    private final FollowService followService;

    @PostMapping
    @Operation(summary = "Follow a user")
    public ResponseEntity<FollowResponse> follow(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FollowRequest request) {
        FollowResponse response = followService.follow(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Unfollow a user")
    public ResponseEntity<Void> unfollow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId) {
        followService.unfollow(principal.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update follow settings")
    public ResponseEntity<FollowResponse> updateFollow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateFollowRequest request) {
        FollowResponse response = followService.updateFollow(principal.getId(), userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/followers")
    @Operation(summary = "Get followers list")
    public ResponseEntity<Page<FollowResponse>> getFollowers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FollowResponse> response = followService.getFollowers(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/following")
    @Operation(summary = "Get following list")
    public ResponseEntity<Page<FollowResponse>> getFollowing(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FollowResponse> response = followService.getFollowing(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/close-friends")
    @Operation(summary = "Get close friends list")
    public ResponseEntity<Page<FollowResponse>> getCloseFriends(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FollowResponse> response = followService.getCloseFriends(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/{userId}")
    @Operation(summary = "Get follow stats for a user")
    public ResponseEntity<FollowStatsResponse> getFollowStats(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId) {
        FollowStatsResponse response = followService.getFollowStats(userId, principal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/following/ids")
    @Operation(summary = "Get following user IDs")
    public ResponseEntity<List<UUID>> getFollowingIds(@AuthenticationPrincipal UserPrincipal principal) {
        List<UUID> ids = followService.getFollowingIds(principal.getId());
        return ResponseEntity.ok(ids);
    }

    @GetMapping("/check/{userId}")
    @Operation(summary = "Check if following a user")
    public ResponseEntity<Boolean> isFollowing(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId) {
        boolean isFollowing = followService.isFollowing(principal.getId(), userId);
        return ResponseEntity.ok(isFollowing);
    }
}
