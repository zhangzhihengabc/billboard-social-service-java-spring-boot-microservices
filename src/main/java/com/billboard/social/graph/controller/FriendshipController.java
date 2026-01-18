package com.billboard.social.graph.controller;

import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.service.FriendshipService;
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
@RequestMapping("/friendships")
@RequiredArgsConstructor
@Tag(name = "Friendships", description = "Friend request and friendship management")
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping("/request")
    @Operation(summary = "Send a friend request")
    public ResponseEntity<FriendshipResponse> sendFriendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FriendRequest request) {
        FriendshipResponse response = friendshipService.sendFriendRequest(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{friendshipId}/accept")
    @Operation(summary = "Accept a friend request")
    public ResponseEntity<FriendshipResponse> acceptFriendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID friendshipId) {
        FriendshipResponse response = friendshipService.acceptFriendRequest(principal.getId(), friendshipId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{friendshipId}/decline")
    @Operation(summary = "Decline a friend request")
    public ResponseEntity<FriendshipResponse> declineFriendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID friendshipId) {
        FriendshipResponse response = friendshipService.declineFriendRequest(principal.getId(), friendshipId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{friendshipId}/cancel")
    @Operation(summary = "Cancel a sent friend request")
    public ResponseEntity<Void> cancelFriendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID friendshipId) {
        friendshipService.cancelFriendRequest(principal.getId(), friendshipId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{friendId}")
    @Operation(summary = "Unfriend a user")
    public ResponseEntity<Void> unfriend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID friendId) {
        friendshipService.unfriend(principal.getId(), friendId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get friends list")
    public ResponseEntity<Page<FriendResponse>> getFriends(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FriendResponse> response = friendshipService.getFriends(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/pending")
    @Operation(summary = "Get pending friend requests")
    public ResponseEntity<Page<FriendshipResponse>> getPendingRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FriendshipResponse> response = friendshipService.getPendingRequests(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/sent")
    @Operation(summary = "Get sent friend requests")
    public ResponseEntity<Page<FriendshipResponse>> getSentRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FriendshipResponse> response = friendshipService.getSentRequests(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ids")
    @Operation(summary = "Get friend IDs")
    public ResponseEntity<List<UUID>> getFriendIds(@AuthenticationPrincipal UserPrincipal principal) {
        List<UUID> friendIds = friendshipService.getFriendIds(principal.getId());
        return ResponseEntity.ok(friendIds);
    }

    @GetMapping("/mutual/{userId}")
    @Operation(summary = "Get mutual friends with a user")
    public ResponseEntity<List<UUID>> getMutualFriends(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId) {
        List<UUID> mutualFriendIds = friendshipService.getMutualFriendIds(principal.getId(), userId);
        return ResponseEntity.ok(mutualFriendIds);
    }

    @GetMapping("/check/{userId}")
    @Operation(summary = "Check if users are friends")
    public ResponseEntity<Boolean> areFriends(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId) {
        boolean areFriends = friendshipService.areFriends(principal.getId(), userId);
        return ResponseEntity.ok(areFriends);
    }

    @GetMapping("/count")
    @Operation(summary = "Get friends count")
    public ResponseEntity<Long> getFriendsCount(@AuthenticationPrincipal UserPrincipal principal) {
        long count = friendshipService.getFriendsCount(principal.getId());
        return ResponseEntity.ok(count);
    }
}
