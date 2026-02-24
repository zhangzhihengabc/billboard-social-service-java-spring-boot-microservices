package com.billboard.social.group.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.group.dto.request.GroupCategoryRequests.*;
import com.billboard.social.group.dto.response.GroupCategoryResponses.*;
import com.billboard.social.group.service.GroupCategoryService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/categories")
@RequiredArgsConstructor
@Validated
@Tag(name = "Group Categories", description = "Group category management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class GroupCategoryController {

    private final GroupCategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create a new category")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Category created successfully",
                    content = @Content(schema = @Schema(implementation = GroupCategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid input or category already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupCategoryResponse> createCategory(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateGroupCategoryRequest request) {
        GroupCategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Get category by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved category",
                    content = @Content(schema = @Schema(implementation = GroupCategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Category not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupCategoryResponse> getCategory(
            @Parameter(description = "ID of the category", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID categoryId) {
        GroupCategoryResponse response = categoryService.getCategory(categoryId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get category by slug")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved category",
                    content = @Content(schema = @Schema(implementation = GroupCategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Category not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupCategoryResponse> getCategoryBySlug(
            @Parameter(description = "Slug of the category", required = true, example = "technology")
            @PathVariable @Size(min = 1, max = 120) String slug) {
        GroupCategoryResponse response = categoryService.getCategoryBySlug(slug);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Update category")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Category updated successfully",
                    content = @Content(schema = @Schema(implementation = GroupCategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Category not found or invalid input",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupCategoryResponse> updateCategory(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the category", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateGroupCategoryRequest request) {
        GroupCategoryResponse response = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "Delete category")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Category deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - Category not found or has groups/subcategories",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteCategory(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the category", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get all categories (paginated)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved categories",
                    content = @Content(schema = @Schema(implementation = GroupCategoryPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupCategoryResponse>> getAllCategories(
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<GroupCategoryResponse> response = categoryService.getAllCategories(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    @Operation(summary = "Get all categories (non-paginated list)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved categories"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<GroupCategoryResponse>> getAllCategoriesList() {
        List<GroupCategoryResponse> response = categoryService.getAllCategoriesList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/root")
    @Operation(summary = "Get root categories (no parent)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved root categories",
                    content = @Content(schema = @Schema(implementation = GroupCategoryPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupCategoryResponse>> getRootCategories(
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<GroupCategoryResponse> response = categoryService.getRootCategories(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{parentId}/subcategories")
    @Operation(summary = "Get subcategories of a category")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved subcategories",
                    content = @Content(schema = @Schema(implementation = GroupCategoryPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Parent category not found or invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupCategoryResponse>> getSubcategories(
            @Parameter(description = "ID of the parent category", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID parentId,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<GroupCategoryResponse> response = categoryService.getSubcategories(parentId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search categories")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved search results",
                    content = @Content(schema = @Schema(implementation = GroupCategoryPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupCategoryResponse>> searchCategories(
            @Parameter(description = "Search query", required = true, example = "tech")
            @RequestParam @Size(min = 1, max = 100) String query,
            @Parameter(description = "Page number (0-indexed)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Number of items per page", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<GroupCategoryResponse> response = categoryService.searchCategories(query, page, size);
        return ResponseEntity.ok(response);
    }

    @Schema(description = "Paginated group category response")
    private static class GroupCategoryPageResponse extends PageResponse<GroupCategoryResponse> {}

    @Schema(description = "Error response")
    private static class ErrorResponse {
        @Schema(description = "Timestamp of the error", example = "2026-01-19T17:30:00Z")
        public String timestamp;
        @Schema(description = "HTTP status code", example = "400")
        public int status;
        @Schema(description = "Error type", example = "Bad Request")
        public String error;
        @Schema(description = "Error message", example = "Category not found")
        public String message;
    }
}