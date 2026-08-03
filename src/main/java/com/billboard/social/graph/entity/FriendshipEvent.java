package com.billboard.social.graph.entity;

import com.billboard.social.graph.entity.enums.FriendshipStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only record of each friendship status transition.
 * Rows are written once and never updated; no BaseEntity (which carries
 * updated_at, deleted_at, and version) is used.
 */
@Entity
@Table(
    name = "friendship_events",
    indexes = {
        @Index(name = "idx_friendship_event_friendship", columnList = "friendship_id"),
        @Index(name = "idx_friendship_event_pair",       columnList = "requester_id, addressee_id")
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FriendshipEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "friendship_id", nullable = false, updatable = false)
    private UUID friendshipId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private Long requesterId;

    @Column(name = "addressee_id", nullable = false, updatable = false)
    private Long addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private FriendshipStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private FriendshipStatus toStatus;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
