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
@Table(name = "event_attendees", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "user_id"}),
    indexes = {
        @Index(name = "idx_attendee_event", columnList = "event_id"),
        @Index(name = "idx_attendee_user", columnList = "user_id"),
        @Index(name = "idx_attendee_status", columnList = "rsvp_status")
    }
)
@SQLDelete(sql = "UPDATE event_attendees SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventAttendee extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rsvp_status", nullable = false, length = 20)
    @Builder.Default
    private RsvpStatus rsvpStatus = RsvpStatus.INVITED;

    @Column(name = "rsvp_at")
    private LocalDateTime rsvpAt;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "guest_count")
    @Builder.Default
    private Integer guestCount = 0;

    @Column(name = "is_host")
    @Builder.Default
    private Boolean isHost = false;

    @Column(name = "is_co_host")
    @Builder.Default
    private Boolean isCoHost = false;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "invited_by")
    private Long invitedBy;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    public void rsvp(RsvpStatus status) {
        this.rsvpStatus = status;
        this.rsvpAt = LocalDateTime.now();
    }

    public void checkIn() {
        this.checkedInAt = LocalDateTime.now();
    }

    public boolean isGoing() {
        return rsvpStatus == RsvpStatus.GOING;
    }

    public boolean isMaybe() {
        return rsvpStatus == RsvpStatus.MAYBE;
    }

    public boolean isInvited() {
        return rsvpStatus == RsvpStatus.INVITED;
    }
}
