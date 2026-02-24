package com.billboard.social.graph.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.service.FriendshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/friendships")
@RequiredArgsConstructor
@Validated
@Tag(name = "Friendships", description = "Friend request and friendship management")
@SecurityRequirement(name = "bearerAuth")
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping("/request")
    @Operation(summary = "Send a friend request", description = "Send a friend request to another user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Friend request sent successfully",
                    content = @Content(schema = @Schema(implementation = FriendshipResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Cannot send to yourself, already friends, request pending, blocked, max limit reached, or user not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FriendshipResponse> sendFriendRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FriendRequest request) {
        FriendshipResponse response = friendshipService.sendFriendRequest(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{friendshipId}/accept")
    @Operation(summary = "Accept a friend request", description = "Accept a pending friend request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Friend request accepted successfully",
                    content = @Content(schema = @Schema(implementation = FriendshipResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Not the addressee, request not pending, or friendship not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FriendshipResponse> acceptFriendRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the friendship request", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID friendshipId) {
        FriendshipResponse response = friendshipService.acceptFriendRequest(principal.getId(), friendshipId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{friendshipId}/decline")
    @Operation(summary = "Decline a friend request", description = "Decline a pending friend request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Friend request declined successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - Not the addressee, request not pending, or friendship not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> declineFriendRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the friendship request", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID friendshipId) {
        friendshipService.declineFriendRequest(principal.getId(), friendshipId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{friendshipId}/cancel")
    @Operation(summary = "Cancel a sent friend request", description = "Cancel a friend request you sent")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Friend request cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - Not the requester, request not pending, or friendship not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> cancelFriendRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the friendship request", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID friendshipId) {
        friendshipService.cancelFriendRequest(principal.getId(), friendshipId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{friendId}")
    @Operation(summary = "Unfriend a user", description = "Remove a user from your friends list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully unfriended the user"),
            @ApiResponse(responseCode = "400", description = "Bad request - Not friends with this user or friendship not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> unfriend(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the friend to remove", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable Long friendId) {
        friendshipService.unfriend(principal.getId(), friendId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get friends list", description = "Get paginated list of your friends")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved friends list",
                    content = @Content(schema = @Schema(implementation = FriendPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<FriendResponse>> getFriends(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<FriendResponse> response = friendshipService.getFriends(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/pending")
    @Operation(summary = "Get pending friend requests", description = "Get paginated list of friend requests you received")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved pending requests",
                    content = @Content(schema = @Schema(implementation = FriendshipPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<FriendshipResponse>> getPendingRequests(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<FriendshipResponse> response = friendshipService.getPendingRequests(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/sent")
    @Operation(summary = "Get sent friend requests", description = "Get paginated list of friend requests you sent")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved sent requests",
                    content = @Content(schema = @Schema(implementation = FriendshipPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<FriendshipResponse>> getSentRequests(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<FriendshipResponse> response = friendshipService.getSentRequests(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ids")
    @Operation(summary = "Get friend IDs", description = "Get list of all friend user IDs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved friend IDs"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<Long>> getFriendIds(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        List<Long> friendIds = friendshipService.getFriendIds(principal.getId());
        return ResponseEntity.ok(friendIds);
    }

    @GetMapping("/mutual/{userId}")
    @Operation(summary = "Get mutual friends", description = "Get list of mutual friend IDs with another user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved mutual friend IDs"),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid userId format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<Long>> getMutualFriends(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the user to find mutual friends with", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable Long userId) {
        List<Long> mutualFriendIds = friendshipService.getMutualFriendIds(principal.getId(), userId);
        return ResponseEntity.ok(mutualFriendIds);
    }

    @GetMapping("/check/{userId}")
    @Operation(summary = "Check if users are friends", description = "Check if you are friends with a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully checked friendship status"),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid userId format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Boolean> areFriends(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the user to check friendship with", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable Long userId) {
        boolean areFriends = friendshipService.areFriends(principal.getId(), userId);
        return ResponseEntity.ok(areFriends);
    }

    @GetMapping("/count")
    @Operation(summary = "Get friends count", description = "Get the total number of friends")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved friends count"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Long> getFriendsCount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        long count = friendshipService.getFriendsCount(principal.getId());
        return ResponseEntity.ok(count);
    }

    @Schema(description = "Paginated friend response")
    private static class FriendPageResponse extends PageResponse<FriendResponse> {}

    @Schema(description = "Paginated friendship response")
    private static class FriendshipPageResponse extends PageResponse<FriendshipResponse> {}

    @Schema(description = "Error response")
    private static class ErrorResponse {
        @Schema(description = "Timestamp of the error", example = "2026-01-19T17:30:00Z")
        public String timestamp;
        @Schema(description = "HTTP status code", example = "400")
        public int status;
        @Schema(description = "Error type", example = "Bad Request")
        public String error;
        @Schema(description = "Error message", example = "Friendship not found")
        public String message;
    }
}