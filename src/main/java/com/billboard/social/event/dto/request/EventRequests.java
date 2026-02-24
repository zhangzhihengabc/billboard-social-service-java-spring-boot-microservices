package com.billboard.social.event.dto.request;

import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.event.entity.enums.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class EventRequests {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateEventRequest {
        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
        private String title;

        @Size(max = 10000, message = "Description cannot exceed 10000 characters")
        private String description;

        private UUID groupId;
        private UUID categoryId;
        private EventType eventType;
        private EventVisibility visibility;
        private String coverImageUrl;

        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future")
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String timezone;
        private Boolean isAllDay;

        // Location
        private String venueName;
        private String address;
        private String city;
        private String country;
        private Double latitude;
        private Double longitude;

        // Online
        private String onlineUrl;
        private String onlinePlatform;

        // Capacity & Ticketing
        @Min(value = 1, message = "Maximum attendees must be at least 1")
        private Integer maxAttendees;
        private Boolean isTicketed;
        @DecimalMin(value = "0.00", message = "Ticket price cannot be negative")
        private BigDecimal ticketPrice;
        private String ticketCurrency;

        // Recurrence
        private RecurrenceType recurrenceType;
        private LocalDateTime recurrenceEndDate;

        // Settings
        private Boolean allowGuests;
        @Min(value = 0)
        @Max(value = 10)
        private Integer guestsPerRsvp;
        private Boolean showGuestList;
        private Boolean allowComments;
        private Boolean requireApproval;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateEventRequest {
        @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
        private String title;

        @Size(max = 10000, message = "Description cannot exceed 10000 characters")
        private String description;

        private UUID categoryId;
        private EventType eventType;
        private EventVisibility visibility;
        private EventStatus status;
        private String coverImageUrl;

        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String timezone;
        private Boolean isAllDay;

        @Schema(description = "Whether event is accepting RSVPs", example = "true")
        private Boolean acceptingRsvps;

        // Location
        private String venueName;
        private String address;
        private String city;
        private String country;

        // Capacity
        private Integer maxAttendees;

        // Settings
        private Boolean allowGuests;
        private Integer guestsPerRsvp;
        private Boolean showGuestList;
        private Boolean allowComments;
        private Boolean requireApproval;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RsvpRequest {
        @NotNull(message = "RSVP status is required")
        private RsvpStatus status;

        @Min(value = 0, message = "Guest count cannot be negative")
        @Max(value = 10, message = "Maximum 10 guests allowed")
        private Integer guestCount = 0;

        @Size(max = 500, message = "Note cannot exceed 500 characters")
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InviteRequest {
        private Long userId;
        private java.util.List<Long> userIds;
        private String email;

        @Size(max = 500, message = "Message cannot exceed 500 characters")
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkInviteRequest {
        @NotEmpty(message = "At least one user ID is required")
        private java.util.List<Long> userIds;

        @Size(max = 500, message = "Message cannot exceed 500 characters")
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoHostRequest {
        @NotNull(message = "User ID is required")
        private Long userId;
    }

    // ==================== CATEGORY REQUESTS (Admin only) ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Create a new event category (Admin only)")
    public static class CreateCategoryRequest {
        @NotBlank(message = "Category name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Schema(description = "Category name", example = "Tech Talk", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @Size(max = 120, message = "Slug cannot exceed 120 characters")
        @Pattern(regexp = "^$|^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
        @Schema(description = "URL-friendly slug (auto-generated from name if not provided)", example = "tech-talk", nullable = true)
        private String slug;

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        @Schema(description = "Category description", example = "Technology discussions and presentations", nullable = true)
        private String description;

        @Size(max = 50, message = "Icon name cannot exceed 50 characters")
        @Schema(description = "Icon identifier for UI", example = "tech", nullable = true)
        private String icon;

        @Pattern(regexp = "^$|^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$", message = "Invalid color format. Use hex like #FF5733")
        @Schema(description = "Color in hex format for UI", example = "#3B82F6", nullable = true)
        private String color;

        @Min(value = 0, message = "Display order must be non-negative")
        @Max(value = 1000, message = "Display order cannot exceed 1000")
        @Schema(description = "Display order for sorting in lists", example = "5", nullable = true)
        private Integer displayOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Update an event category (Admin only)")
    public static class UpdateCategoryRequest {
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Schema(description = "Category name", example = "Updated Tech Talk", nullable = true)
        private String name;

        @Size(max = 120, message = "Slug cannot exceed 120 characters")
        @Pattern(regexp = "^$|^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
        @Schema(description = "URL-friendly slug", example = "updated-tech-talk", nullable = true)
        private String slug;

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        @Schema(description = "Category description", nullable = true)
        private String description;

        @Size(max = 50, message = "Icon name cannot exceed 50 characters")
        @Schema(description = "Icon identifier for UI", nullable = true)
        private String icon;

        @Pattern(regexp = "^$|^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$", message = "Invalid color format. Use hex like #FF5733")
        @Schema(description = "Color in hex format for UI", example = "#10B981", nullable = true)
        private String color;

        @Min(value = 0, message = "Display order must be non-negative")
        @Max(value = 1000, message = "Display order cannot exceed 1000")
        @Schema(description = "Display order for sorting", nullable = true)
        private Integer displayOrder;

        @Schema(description = "Whether category is active and visible to users", example = "true", nullable = true)
        private Boolean isActive;
    }

    // ============================================================
// ADD TO EventRequests.java
// ============================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Request to add a co-host to an event")
    public static class AddCoHostRequest {

        @NotNull(message = "User ID is required")
        @Schema(description = "User ID to add as co-host", required = true,
                example = "1")
        private Long userId;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Request to update co-host permissions")
    public static class UpdateCoHostRequest {

        @Schema(description = "Can co-host edit event details", example = "true")
        private Boolean canEdit;

        @Schema(description = "Can co-host invite users", example = "true")
        private Boolean canInvite;

        @Schema(description = "Can co-host manage RSVPs (check-in attendees)", example = "true")
        private Boolean canManageRsvps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Co-host details response")
    public static class CoHostResponse {

        @Schema(description = "Co-host record ID")
        private UUID id;

        @Schema(description = "Event ID")
        private UUID eventId;

        @Schema(description = "Co-host user ID")
        private Long userId;

        @Schema(description = "Can edit event details")
        private Boolean canEdit;

        @Schema(description = "Can invite users")
        private Boolean canInvite;

        @Schema(description = "Can manage RSVPs (check-in attendees)")
        private Boolean canManageRsvps;

        @Schema(description = "When co-host was added", example = "2026-01-25T10:30:00Z")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;

        @Schema(description = "Co-host user details")
        private UserSummary user;
    }
}