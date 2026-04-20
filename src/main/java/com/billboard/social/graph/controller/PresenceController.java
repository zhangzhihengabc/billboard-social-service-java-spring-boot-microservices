package com.billboard.social.graph.controller;

import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.dto.response.SocialResponses.FriendPresenceResponse;
import com.billboard.social.graph.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/presence")
@RequiredArgsConstructor
@Tag(name = "Presence", description = "Online presence and active friends")
@SecurityRequirement(name = "bearerAuth")
public class PresenceController {

    private final PresenceService presenceService;

    /**
     * Heartbeat endpoint. The client should call this once per minute while the
     * user is active. Presence expires automatically after 60 s (configurable)
     * if no heartbeat is received.
     */
    @PutMapping("/heartbeat")
    @Operation(
            summary = "Send a heartbeat",
            description = "Mark the authenticated user as online. Must be called at least once per minute " +
                    "to remain visible as online to friends."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Heartbeat acknowledged — user is now marked as online"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> heartbeat(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        presenceService.markOnline(principal.getId());
        return ResponseEntity.ok().build();
    }

    /**
     * Returns all accepted friends of the authenticated user who are currently
     * online (i.e. have sent a heartbeat within the TTL window).
     */
    @GetMapping("/friends")
    @Operation(
            summary = "Get online friends",
            description = "Returns the list of the authenticated user's friends who are currently online."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved online friends",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = FriendPresenceResponse.class)))
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<FriendPresenceResponse>> getOnlineFriends(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        List<FriendPresenceResponse> response = presenceService.getOnlineFriends(principal.getId());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Swagger schema helpers (not exposed as endpoints)
    // -------------------------------------------------------------------------

    @Schema(description = "Error response")
    private static class ErrorResponse {
        @Schema(description = "Timestamp of the error", example = "2026-01-19T17:30:00Z")
        public String timestamp;
        @Schema(description = "HTTP status code", example = "401")
        public int status;
        @Schema(description = "Error type", example = "Unauthorized")
        public String error;
        @Schema(description = "Error message", example = "Full authentication is required to access this resource")
        public String message;
    }
}