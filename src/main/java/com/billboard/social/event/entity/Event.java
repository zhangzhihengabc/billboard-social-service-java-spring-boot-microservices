package com.billboard.social.event.entity;
import com.billboard.social.common.entity.BaseEntity;

import com.billboard.social.event.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_event_host", columnList = "host_id"),
    @Index(name = "idx_event_start", columnList = "start_time"),
    @Index(name = "idx_event_status", columnList = "status"),
    @Index(name = "idx_event_visibility", columnList = "visibility"),
    @Index(name = "idx_event_group", columnList = "group_id"),
    @Index(name = "idx_event_category", columnList = "category_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "slug", nullable = false, unique = true, length = 250)
    private String slug;

    @Column(name = "description", length = 10000)
    private String description;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    @Builder.Default
    private EventType eventType = EventType.IN_PERSON;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    @Builder.Default
    private EventVisibility visibility = EventVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    // Date and Time
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "is_all_day")
    @Builder.Default
    private Boolean isAllDay = false;

    // Location
    @Column(name = "venue_name", length = 200)
    private String venueName;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    // Online event
    @Column(name = "online_url", length = 500)
    private String onlineUrl;

    @Column(name = "online_platform", length = 50)
    private String onlinePlatform;

    // Capacity
    @Column(name = "max_attendees")
    private Integer maxAttendees;

    @Column(name = "going_count")
    @Builder.Default
    private Integer goingCount = 0;

    @Column(name = "maybe_count")
    @Builder.Default
    private Integer maybeCount = 0;

    @Column(name = "invited_count")
    @Builder.Default
    private Integer invitedCount = 0;

    // Ticketing
    @Column(name = "is_ticketed")
    @Builder.Default
    private Boolean isTicketed = false;

    @Column(name = "ticket_price", precision = 10, scale = 2)
    private BigDecimal ticketPrice;

    @Column(name = "ticket_currency", length = 3)
    private String ticketCurrency;

    @Column(name = "tickets_sold")
    @Builder.Default
    private Integer ticketsSold = 0;

    // Recurrence
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", length = 20)
    @Builder.Default
    private RecurrenceType recurrenceType = RecurrenceType.NONE;

    @Column(name = "recurrence_end_date")
    private LocalDateTime recurrenceEndDate;

    @Column(name = "parent_event_id")
    private UUID parentEventId;

    // Settings
    @Column(name = "allow_guests")
    @Builder.Default
    private Boolean allowGuests = true;

    @Column(name = "guests_per_rsvp")
    @Builder.Default
    private Integer guestsPerRsvp = 1;

    @Column(name = "show_guest_list")
    @Builder.Default
    private Boolean showGuestList = true;

    @Column(name = "accepting_rsvps")
    @Builder.Default
    private Boolean acceptingRsvps = true;

    @Column(name = "allow_comments")
    @Builder.Default
    private Boolean allowComments = true;

    @Column(name = "require_approval")
    @Builder.Default
    private Boolean requireApproval = false;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<EventRsvp> rsvps = new HashSet<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<EventCoHost> coHosts = new HashSet<>();

    public void incrementGoingCount() {
        this.goingCount++;
    }

    public void decrementGoingCount() {
        if (this.goingCount > 0) this.goingCount--;
    }

    public void incrementMaybeCount() {
        this.maybeCount++;
    }

    public void decrementMaybeCount() {
        if (this.maybeCount > 0) this.maybeCount--;
    }

    public boolean isFull() {
        return maxAttendees != null && goingCount >= maxAttendees;
    }

    public boolean hasCapacity() {
        return maxAttendees == null || goingCount < maxAttendees;
    }

    public boolean isUpcoming() {
        return startTime.isAfter(LocalDateTime.now());
    }

    public boolean isOngoing() {
        LocalDateTime now = LocalDateTime.now();
        return startTime.isBefore(now) && (endTime == null || endTime.isAfter(now));
    }

    public boolean isPast() {
        LocalDateTime now = LocalDateTime.now();
        return endTime != null ? endTime.isBefore(now) : startTime.isBefore(now);
    }

}
