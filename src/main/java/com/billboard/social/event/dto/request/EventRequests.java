package com.billboard.social.event.dto.request;

import com.billboard.social.event.entity.enums.*;
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
        private UUID userId;
        private java.util.List<UUID> userIds;
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
        private java.util.List<UUID> userIds;
        
        @Size(max = 500, message = "Message cannot exceed 500 characters")
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoHostRequest {
        @NotNull(message = "User ID is required")
        private UUID userId;
    }
}
