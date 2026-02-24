package com.billboard.social.group.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.group.dto.request.GroupRequests.*;
import com.billboard.social.group.dto.response.GroupResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.group.service.GroupService;
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
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@Validated
@Tag(name = "Groups", description = "Group management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @Operation(summary = "Create a new group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Group created successfully",
                    content = @Content(schema = @Schema(implementation = GroupResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid input or max group limit reached",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupResponse> createGroup(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateGroupRequest request) {
        GroupResponse response = groupService.createGroup(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get group by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved group",
                    content = @Content(schema = @Schema(implementation = GroupResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Group not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Private group access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupResponse> getGroup(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the group", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID groupId) {
        Long userId = principal != null ? principal.getId() : null;
        GroupResponse response = groupService.getGroup(groupId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get group by slug")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved group",
                    content = @Content(schema = @Schema(implementation = GroupResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Group not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Private group access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupResponse> getGroupBySlug(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Slug of the group", required = true, example = "my-awesome-group")
            @PathVariable @Size(min = 1, max = 200) String slug) {
        Long userId = principal != null ? principal.getId() : null;
        GroupResponse response = groupService.getGroupBySlug(slug, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "Update group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Group updated successfully",
                    content = @Content(schema = @Schema(implementation = GroupResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Group not found or invalid input",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupResponse> updateGroup(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the group", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        GroupResponse response = groupService.updateGroup(principal.getId(), groupId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "Delete group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Group deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - Group not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only owner can delete",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteGroup(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the group", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID groupId) {
        groupService.deleteGroup(principal.getId(), groupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search groups")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved search results",
                    content = @Content(schema = @Schema(implementation = GroupSummaryPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupSummaryResponse>> searchGroups(
            @Parameter(description = "Search query", required = true, example = "music")
            @RequestParam @Size(min = 1, max = 100) String query,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<GroupSummaryResponse> response = groupService.searchGroups(query, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/popular")
    @Operation(summary = "Get popular groups")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved popular groups",
                    content = @Content(schema = @Schema(implementation = GroupSummaryPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupSummaryResponse>> getPopularGroups(
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<GroupSummaryResponse> response = groupService.getPopularGroups(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/featured")
    @Operation(summary = "Get featured groups")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved featured groups",
                    content = @Content(schema = @Schema(implementation = GroupSummaryPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupSummaryResponse>> getFeaturedGroups(
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<GroupSummaryResponse> response = groupService.getFeaturedGroups(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Get groups by category")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved groups by category",
                    content = @Content(schema = @Schema(implementation = GroupSummaryPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupSummaryResponse>> getGroupsByCategory(
            @Parameter(description = "ID of the category", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID categoryId,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<GroupSummaryResponse> response = groupService.getGroupsByCategory(categoryId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    @Operation(summary = "Get current user's groups")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user's groups",
                    content = @Content(schema = @Schema(implementation = MembershipPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<MembershipResponse>> getMyGroups(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<MembershipResponse> response = groupService.getUserGroups(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @Schema(description = "Paginated group summary response")
    private static class GroupSummaryPageResponse extends PageResponse<GroupSummaryResponse> {}

    @Schema(description = "Paginated membership response")
    private static class MembershipPageResponse extends PageResponse<MembershipResponse> {}

    @Schema(description = "Error response")
    private static class ErrorResponse {
        @Schema(description = "Timestamp of the error", example = "2026-01-19T17:30:00Z")
        public String timestamp;
        @Schema(description = "HTTP status code", example = "400")
        public int status;
        @Schema(description = "Error type", example = "Bad Request")
        public String error;
        @Schema(description = "Error message", example = "Group not found")
        public String message;
    }
}