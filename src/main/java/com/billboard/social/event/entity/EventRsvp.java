package com.billboard.social.event.entity;
import com.billboard.social.common.entity.BaseEntity;

import com.billboard.social.event.entity.enums.RsvpStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_rsvps",
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "user_id"}),
    indexes = {
        @Index(name = "idx_rsvp_event", columnList = "event_id"),
        @Index(name = "idx_rsvp_user", columnList = "user_id"),
        @Index(name = "idx_rsvp_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRsvp extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RsvpStatus status = RsvpStatus.INVITED;

    @Column(name = "guest_count")
    @Builder.Default
    private Integer guestCount = 0;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "notifications_enabled")
    @Builder.Default
    private Boolean notificationsEnabled = true;

    public void respond(RsvpStatus newStatus) {
        this.status = newStatus;
        this.respondedAt = LocalDateTime.now();
    }

    public void checkIn() {
        this.checkedInAt = LocalDateTime.now();
    }

    public boolean isGoing() {
        return status == RsvpStatus.GOING;
    }

    public boolean isMaybe() {
        return status == RsvpStatus.MAYBE;
    }

    public boolean hasResponded() {
        return status != RsvpStatus.INVITED;
    }
}
