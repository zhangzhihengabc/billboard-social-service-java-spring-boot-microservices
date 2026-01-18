package com.billboard.social.event.dto.response;

import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.event.entity.enums.*;
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
        private String description;
        private UUID hostId;
        private UUID groupId;
        private UUID categoryId;
        private String categoryName;
        private EventType eventType;
        private EventVisibility visibility;
        private EventStatus status;
        private String coverImageUrl;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String timezone;
        private Boolean isAllDay;
        private String venueName;
        private String address;
        private String city;
        private String country;
        private Integer maxAttendees;
        private Integer goingCount;
        private Integer maybeCount;
        private Integer invitedCount;
        private Boolean isTicketed;
        private BigDecimal ticketPrice;
        private String ticketCurrency;
        private RecurrenceType recurrenceType;
        private Boolean allowGuests;
        private Boolean showGuestList;
        private LocalDateTime createdAt;
        // Related entities
        private UserSummary host;
        private List<UserSummary> coHosts;
        // Current user context
        private Boolean isHost;
        private Boolean isCoHost;
        private RsvpStatus userRsvpStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventSummaryResponse {
        private UUID id;
        private String title;
        private String slug;
        private String coverImageUrl;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String venueName;
        private String city;
        private EventType eventType;
        private Integer goingCount;
        private Boolean isTicketed;
        private BigDecimal ticketPrice;
        private String ticketCurrency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RsvpResponse {
        private UUID id;
        private UUID eventId;
        private UUID userId;
        private RsvpStatus status;
        private Integer guestCount;
        private String note;
        private LocalDateTime respondedAt;
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
        private UUID userId;
        private RsvpStatus rsvpStatus;
        private LocalDateTime rsvpAt;
        private LocalDateTime checkedInAt;
        private Integer guestCount;
        private Boolean isHost;
        private Boolean isCoHost;
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
        private UUID userId;
        private String username;
        private String displayName;
        private String avatarUrl;
        private Boolean canEdit;
        private Boolean canInvite;
        private Boolean canManageRsvps;
        private LocalDateTime addedAt;
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
        private String description;
        private String iconUrl;
        private Integer eventCount;
    }
}
