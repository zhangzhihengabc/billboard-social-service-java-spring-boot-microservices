package com.billboard.social.group.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

public class GroupCategoryResponses {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Group category response")
    public static class GroupCategoryResponse {

        @Schema(description = "Category ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;

        @Schema(description = "Category name", example = "Technology")
        private String name;

        @Schema(description = "Category slug", example = "technology")
        private String slug;

        @Schema(description = "Category description", example = "Tech communities and discussions", nullable = true)
        private String description;

        @Schema(description = "Icon identifier or emoji", example = "💻", nullable = true)
        private String icon;

        @Schema(description = "Parent category ID", example = "550e8400-e29b-41d4-a716-446655440000", nullable = true)
        private UUID parentId;

        @Schema(description = "Display order", example = "1")
        private Integer displayOrder;

        @Schema(description = "Number of groups in this category", example = "42")
        private Integer groupCount;

        @Schema(description = "Whether category is active", example = "true")
        private Boolean isActive;

        @Schema(description = "When the category was created", example = "2026-01-20T10:30:00Z",
                type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Simplified category response for lists")
    public static class GroupCategorySummaryResponse {

        @Schema(description = "Category ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;

        @Schema(description = "Category name", example = "Technology")
        private String name;

        @Schema(description = "Category slug", example = "technology")
        private String slug;

        @Schema(description = "Icon identifier or emoji", example = "💻", nullable = true)
        private String icon;

        @Schema(description = "Number of groups in this category", example = "42")
        private Integer groupCount;
    }
}