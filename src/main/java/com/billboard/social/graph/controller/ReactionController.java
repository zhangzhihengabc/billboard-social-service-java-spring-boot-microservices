package com.billboard.social.graph.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.entity.enums.ReactionType;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.service.ReactionService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reactions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reactions", description = "Content reactions (likes, loves, etc.)")
@SecurityRequirement(name = "bearerAuth")
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping
    @Operation(summary = "Add or update a reaction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Reaction added successfully",
                    content = @Content(schema = @Schema(implementation = ReactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid content type, content ID, or reaction type",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ReactionResponse> react(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReactionRequest request) {
        ReactionResponse response = reactionService.react(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{contentType}/{contentId}")
    @Operation(summary = "Remove a reaction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Reaction removed successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - Reaction not found or invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> removeReaction(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Type of content", required = true, example = "POST",
                    schema = @Schema(implementation = ContentType.class))
            @PathVariable ContentType contentType,
            @Parameter(description = "ID of the content", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID contentId) {
        reactionService.removeReaction(principal.getId(), contentType, contentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{contentType}/{contentId}")
    @Operation(summary = "Get reactions for content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved reactions",
                    content = @Content(schema = @Schema(implementation = ReactionPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<ReactionResponse>> getReactions(
            @Parameter(description = "Type of content", required = true, example = "POST",
                    schema = @Schema(implementation = ContentType.class))
            @PathVariable ContentType contentType,
            @Parameter(description = "ID of the content", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID contentId,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<ReactionResponse> response = reactionService.getReactions(contentType, contentId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{contentType}/{contentId}/type/{reactionType}")
    @Operation(summary = "Get reactions by type for content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved reactions by type",
                    content = @Content(schema = @Schema(implementation = ReactionPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<ReactionResponse>> getReactionsByType(
            @Parameter(description = "Type of content", required = true, example = "POST",
                    schema = @Schema(implementation = ContentType.class))
            @PathVariable ContentType contentType,
            @Parameter(description = "ID of the content", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID contentId,
            @Parameter(description = "Type of reaction", required = true, example = "LIKE",
                    schema = @Schema(implementation = ReactionType.class))
            @PathVariable ReactionType reactionType,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<ReactionResponse> response = reactionService.getReactionsByType(
                contentType, contentId, reactionType, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{contentType}/{contentId}/stats")
    @Operation(summary = "Get reaction statistics for content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved reaction stats",
                    content = @Content(schema = @Schema(implementation = ReactionStatsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ReactionStatsResponse> getReactionStats(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Type of content", required = true, example = "POST",
                    schema = @Schema(implementation = ContentType.class))
            @PathVariable ContentType contentType,
            @Parameter(description = "ID of the content", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID contentId) {
        Long userId = principal != null ? principal.getId() : null;
        ReactionStatsResponse response = reactionService.getReactionStats(userId, contentType, contentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{contentType}/{contentId}/check")
    @Operation(summary = "Check if user has reacted")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully checked reaction status"),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Boolean> hasUserReacted(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Type of content", required = true, example = "POST",
                    schema = @Schema(implementation = ContentType.class))
            @PathVariable ContentType contentType,
            @Parameter(description = "ID of the content", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID contentId) {
        boolean hasReacted = reactionService.hasUserReacted(principal.getId(), contentType, contentId);
        return ResponseEntity.ok(hasReacted);
    }

    @Schema(description = "Paginated reaction response")
    private static class ReactionPageResponse extends PageResponse<ReactionResponse> {}

    @Schema(description = "Error response")
    private static class ErrorResponse {
        @Schema(description = "Timestamp of the error", example = "2026-01-19T17:30:00Z")
        public String timestamp;
        @Schema(description = "HTTP status code", example = "400")
        public int status;
        @Schema(description = "Error type", example = "Bad Request")
        public String error;
        @Schema(description = "Error message", example = "Reaction not found")
        public String message;
    }
}