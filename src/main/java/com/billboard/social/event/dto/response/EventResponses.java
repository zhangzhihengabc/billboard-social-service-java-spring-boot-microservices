package com.billboard.social.event.dto.response;

import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.event.entity.enums.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class EventResponses {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventResponse {
        private UUID id;
        private String title;
        private String slug;

        @Schema(description = "Event description", nullable = true)
        private String description;

        private Long hostId;

        @Schema(description = "Group ID if group event", nullable = true)
        private UUID groupId;

        @Schema(description = "Category ID", nullable = true)
        private UUID categoryId;

        @Schema(description = "Category name", nullable = true)
        private String categoryName;

        private EventType eventType;
        private EventVisibility visibility;
        private EventStatus status;

        @Schema(description = "Whether event is accepting RSVPs")
        private Boolean acceptingRsvps;

        @Schema(description = "Cover image URL", nullable = true)
        private String coverImageUrl;

        @Schema(description = "Event start time", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime startTime;

        @Schema(description = "Event end time", example = "2026-01-19T14:33:16Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime endTime;

        @Schema(description = "Timezone", example = "UTC", nullable = true)
        private String timezone;

        private Boolean isAllDay;

        @Schema(description = "Venue name", nullable = true)
        private String venueName;

        @Schema(description = "Address", nullable = true)
        private String address;

        @Schema(description = "City", nullable = true)
        private String city;

        @Schema(description = "Country", nullable = true)
        private String country;

        @Schema(description = "Maximum attendees", nullable = true)
        private Integer maxAttendees;

        private Integer goingCount;
        private Integer maybeCount;
        private Integer invitedCount;
        private Boolean isTicketed;

        @Schema(description = "Ticket price", nullable = true)
        private BigDecimal ticketPrice;

        @Schema(description = "Ticket currency", nullable = true)
        private String ticketCurrency;

        @Schema(description = "Recurrence type", nullable = true)
        private RecurrenceType recurrenceType;

        private Boolean allowGuests;
        private Boolean showGuestList;

        @Schema(description = "When the event was created", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;

        // Related entities
        private UserSummary host;

        @Schema(description = "Co-hosts list", nullable = true)
        private List<UserSummary> coHosts;

        // Current user context
        @Schema(description = "Is current user the host", nullable = true)
        private Boolean isHost;

        @Schema(description = "Is current user a co-host", nullable = true)
        private Boolean isCoHost;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventSummaryResponse {
        private UUID id;
        private String title;
        private String slug;

        @Schema(description = "Cover image URL", nullable = true)
        private String coverImageUrl;

        @Schema(description = "Event start time", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime startTime;

        @Schema(description = "Event end time", example = "2026-01-19T14:33:16Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime endTime;

        @Schema(description = "Venue name", nullable = true)
        private String venueName;

        @Schema(description = "City", nullable = true)
        private String city;

        private EventType eventType;
        private Integer goingCount;
        private Boolean isTicketed;

        @Schema(description = "Ticket price", nullable = true)
        private BigDecimal ticketPrice;

        @Schema(description = "Ticket currency", nullable = true)
        private String ticketCurrency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RsvpResponse {
        private UUID id;
        private UUID eventId;
        private Long userId;
        private RsvpStatus status;

        @Schema(description = "Guest count", nullable = true)
        private Integer guestCount;

        @Schema(description = "RSVP note", nullable = true)
        private String note;

        @Schema(description = "When user responded", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime respondedAt;

        @Schema(description = "When user checked in", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime checkedInAt;

        private Boolean notificationsEnabled;
        private UserSummary user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendeeResponse {
        private UUID id;
        private UUID eventId;
        private Long userId;
        private RsvpStatus rsvpStatus;

        @Schema(description = "When user RSVPed", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime rsvpAt;

        @Schema(description = "When user checked in", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime checkedInAt;

        @Schema(description = "Guest count", nullable = true)
        private Integer guestCount;

        private Boolean isHost;
        private Boolean isCoHost;

        @Schema(description = "RSVP note", nullable = true)
        private String note;

        private UserSummary user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoHostResponse {
        private UUID id;
        private UUID eventId;
        private Long userId;
        private String username;

        @Schema(description = "Display name", nullable = true)
        private String displayName;

        @Schema(description = "Avatar URL", nullable = true)
        private String avatarUrl;

        @Schema(description = "When co-host was added", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime addedAt;

        private UserSummary user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventStatsResponse {
        private UUID eventId;
        private Long goingCount;
        private Long maybeCount;
        private Long invitedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryResponse {
        private UUID id;
        private String name;
        private String slug;

        @Schema(description = "Category description", nullable = true)
        private String description;

        @Schema(description = "Icon name", nullable = true)
        private String icon;

        @Schema(description = "Color hex code", example = "#3B82F6", nullable = true)
        private String color;

        @Schema(description = "Display order for sorting", example = "1")
        private Integer displayOrder;

        @Schema(description = "Number of events in this category", example = "0")
        private Integer eventCount;

        @Schema(description = "Whether category is active", example = "true")
        private Boolean isActive;

        @Schema(description = "When created", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;

        @Schema(description = "When last updated", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime updatedAt;
    }
}