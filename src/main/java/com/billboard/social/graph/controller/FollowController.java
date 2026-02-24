package com.billboard.social.graph.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.service.FollowService;
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
@RequestMapping("/follows")
@RequiredArgsConstructor
@Validated
@Tag(name = "Follows", description = "APIs for managing follow/unfollow relationships between users")
@SecurityRequirement(name = "bearerAuth")
public class FollowController {

    private final FollowService followService;

    @PostMapping
    @Operation(
            summary = "Follow a user",
            description = "Creates a follow relationship from the authenticated user to the target user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Successfully followed the user",
                    content = @Content(schema = @Schema(implementation = FollowResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Cannot follow yourself, already following, user blocked, or max limit reached",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<FollowResponse> follow(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FollowRequest request) {
        FollowResponse response = followService.follow(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{userId}")
    @Operation(
            summary = "Unfollow a user",
            description = "Removes the follow relationship from the authenticated user to the target user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Successfully unfollowed the user"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Invalid userId format or follow relationship not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Void> unfollow(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the user to unfollow", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable Long userId) {
        followService.unfollow(principal.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}")
    @Operation(
            summary = "Update follow settings",
            description = "Updates follow settings like notifications enabled, close friend status, or mute status"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully updated follow settings",
                    content = @Content(schema = @Schema(implementation = FollowResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Invalid data, invalid userId format, or follow relationship not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<FollowResponse> updateFollow(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the followed user", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable Long userId,
            @Valid @RequestBody UpdateFollowRequest request) {
        FollowResponse response = followService.updateFollow(principal.getId(), userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/followers")
    @Operation(
            summary = "Get followers list",
            description = "Retrieves a paginated list of users who follow the authenticated user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved followers list",
                    content = @Content(schema = @Schema(implementation = FollowPageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<PageResponse<FollowResponse>> getFollowers(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<FollowResponse> response = followService.getFollowers(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/following")
    @Operation(
            summary = "Get following list",
            description = "Retrieves a paginated list of users that the authenticated user is following"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved following list",
                    content = @Content(schema = @Schema(implementation = FollowPageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<PageResponse<FollowResponse>> getFollowing(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<FollowResponse> response = followService.getFollowing(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/close-friends")
    @Operation(
            summary = "Get close friends list",
            description = "Retrieves a paginated list of users marked as close friends by the authenticated user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved close friends list",
                    content = @Content(schema = @Schema(implementation = FollowPageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<PageResponse<FollowResponse>> getCloseFriends(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<FollowResponse> response = followService.getCloseFriends(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/{userId}")
    @Operation(
            summary = "Get follow statistics",
            description = "Retrieves follow statistics (followers count, following count) for a specific user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved follow statistics",
                    content = @Content(schema = @Schema(implementation = FollowStatsResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Invalid userId format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<FollowStatsResponse> getFollowStats(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the user to get stats for", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable Long userId) {
        FollowStatsResponse response = followService.getFollowStats(userId, principal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/following/ids")
    @Operation(
            summary = "Get following user IDs",
            description = "Retrieves a list of user IDs that the authenticated user is following"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved following user IDs"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<List<Long>> getFollowingIds(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        List<Long> ids = followService.getFollowingIds(principal.getId());
        return ResponseEntity.ok(ids);
    }

    @GetMapping("/check/{userId}")
    @Operation(
            summary = "Check if following a user",
            description = "Checks whether the authenticated user is following the specified user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully checked follow status. Returns true if following, false otherwise."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Invalid userId format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Boolean> isFollowing(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the user to check", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable Long userId) {
        boolean isFollowing = followService.isFollowing(principal.getId(), userId);
        return ResponseEntity.ok(isFollowing);
    }

    // Schema classes for Swagger documentation
    @Schema(description = "Paginated follow response")
    private static class FollowPageResponse extends PageResponse<FollowResponse> {}

    @Schema(description = "Error response")
    private static class ErrorResponse {
        @Schema(description = "Timestamp of the error in ISO 8601 format", example = "2026-01-19T17:30:00.000000Z")
        public String timestamp;

        @Schema(description = "HTTP status code", example = "400")
        public int status;

        @Schema(description = "Error type", example = "Bad Request")
        public String error;

        @Schema(description = "Error message", example = "Follow relationship not found")
        public String message;
    }
}