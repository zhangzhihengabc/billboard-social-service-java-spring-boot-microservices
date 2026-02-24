package com.billboard.social.graph.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.service.PokeService;
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
@RequestMapping("/api/v1/pokes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Pokes", description = "Poke feature")
@SecurityRequirement(name = "bearerAuth")
public class PokeController {

    private final PokeService pokeService;

    @PostMapping
    @Operation(summary = "Poke a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User poked successfully",
                    content = @Content(schema = @Schema(implementation = PokeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Cannot poke yourself, user blocked, or poke already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PokeResponse> poke(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PokeRequest request) {
        PokeResponse response = pokeService.poke(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{pokeId}/poke-back")
    @Operation(summary = "Poke back")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Poked back successfully",
                    content = @Content(schema = @Schema(implementation = PokeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Poke not found, not the poked user, or poke inactive",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PokeResponse> pokeBack(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the poke to respond to", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID pokeId) {
        PokeResponse response = pokeService.pokeBack(principal.getId(), pokeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/received")
    @Operation(summary = "Get received pokes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved received pokes",
                    content = @Content(schema = @Schema(implementation = PokePageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<PokeResponse>> getReceivedPokes(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<PokeResponse> response = pokeService.getReceivedPokes(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sent")
    @Operation(summary = "Get sent pokes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved sent pokes",
                    content = @Content(schema = @Schema(implementation = PokePageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<PokeResponse>> getSentPokes(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<PokeResponse> response = pokeService.getSentPokes(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count")
    @Operation(summary = "Get active pokes count")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved active pokes count"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Long> getActivePokesCount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        long count = pokeService.getActivePokesCount(principal.getId());
        return ResponseEntity.ok(count);
    }

    @DeleteMapping("/{pokeId}/dismiss")
    @Operation(summary = "Dismiss a poke")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Poke dismissed successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - Poke not found, not the poked user, or already dismissed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> dismissPoke(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the poke to dismiss", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID pokeId) {
        pokeService.dismissPoke(principal.getId(), pokeId);
        return ResponseEntity.noContent().build();
    }

    @Schema(description = "Paginated poke response")
    private static class PokePageResponse extends PageResponse<PokeResponse> {}

    @Schema(description = "Error response")
    private static class ErrorResponse {
        @Schema(description = "Timestamp of the error", example = "2026-01-19T17:30:00Z")
        public String timestamp;
        @Schema(description = "HTTP status code", example = "400")
        public int status;
        @Schema(description = "Error type", example = "Bad Request")
        public String error;
        @Schema(description = "Error message", example = "Poke not found")
        public String message;
    }
}