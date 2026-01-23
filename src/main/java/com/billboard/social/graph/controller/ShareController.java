package com.billboard.social.graph.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.service.ShareService;
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
@RequestMapping("/shares")
@RequiredArgsConstructor
@Validated
@Tag(name = "Shares", description = "Content sharing")
@SecurityRequirement(name = "bearerAuth")
public class ShareController {

    private final ShareService shareService;

    @PostMapping
    @Operation(summary = "Share content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Content shared successfully",
                    content = @Content(schema = @Schema(implementation = ShareResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid content, blocked user, or missing required fields",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ShareResponse> share(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ShareRequest request) {
        ShareResponse response = shareService.share(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/content/{contentType}/{contentId}")
    @Operation(summary = "Get shares for content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved shares",
                    content = @Content(schema = @Schema(implementation = SharePageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<ShareResponse>> getSharesByContent(
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
        PageResponse<ShareResponse> response = shareService.getSharesByContent(contentType, contentId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user")
    @Operation(summary = "Get shares by current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user shares",
                    content = @Content(schema = @Schema(implementation = SharePageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<ShareResponse>> getSharesByUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<ShareResponse> response = shareService.getSharesByUser(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count/{contentType}/{contentId}")
    @Operation(summary = "Get share count for content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved share count"),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Long> getShareCount(
            @Parameter(description = "Type of content", required = true, example = "POST",
                    schema = @Schema(implementation = ContentType.class))
            @PathVariable ContentType contentType,
            @Parameter(description = "ID of the content", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID contentId) {
        long count = shareService.getShareCount(contentType, contentId);
        return ResponseEntity.ok(count);
    }

    @Schema(description = "Paginated share response")
    private static class SharePageResponse extends PageResponse<ShareResponse> {}

    @Schema(description = "Error response")
    private static class ErrorResponse {
        @Schema(description = "Timestamp of the error", example = "2026-01-19T17:30:00Z")
        public String timestamp;
        @Schema(description = "HTTP status code", example = "400")
        public int status;
        @Schema(description = "Error type", example = "Bad Request")
        public String error;
        @Schema(description = "Error message", example = "Content ID is required")
        public String message;
    }
}