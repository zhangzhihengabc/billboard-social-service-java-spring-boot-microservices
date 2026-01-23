package com.billboard.social.group.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class GroupCategoryRequests {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Request to create a group category")
    public static class CreateGroupCategoryRequest {

        @NotBlank(message = "Category name is required")
        @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
        @Schema(description = "Category name", example = "Technology", required = true)
        private String name;

        @Size(max = 500, message = "Description must be at most 500 characters")
        @Schema(description = "Category description", example = "Tech communities and discussions", nullable = true)
        private String description;

        @Size(max = 50, message = "Icon must be at most 50 characters")
        @Schema(description = "Icon identifier or emoji", example = "💻", nullable = true)
        private String icon;

        @Schema(description = "Parent category ID for subcategories", example = "550e8400-e29b-41d4-a716-446655440000", nullable = true)
        private UUID parentId;

        @Schema(description = "Display order for sorting", example = "1", nullable = true)
        private Integer displayOrder;

        @Schema(description = "Whether category is active", example = "true", nullable = true)
        private Boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Request to update a group category")
    public static class UpdateGroupCategoryRequest {

        @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
        @Schema(description = "Category name", example = "Technology", nullable = true)
        private String name;

        @Size(max = 500, message = "Description must be at most 500 characters")
        @Schema(description = "Category description", example = "Tech communities and discussions", nullable = true)
        private String description;

        @Size(max = 50, message = "Icon must be at most 50 characters")
        @Schema(description = "Icon identifier or emoji", example = "💻", nullable = true)
        private String icon;

        @Schema(description = "Parent category ID for subcategories", example = "550e8400-e29b-41d4-a716-446655440000", nullable = true)
        private UUID parentId;

        @Schema(description = "Display order for sorting", example = "1", nullable = true)
        private Integer displayOrder;

        @Schema(description = "Whether category is active", example = "true", nullable = true)
        private Boolean isActive;
    }
}