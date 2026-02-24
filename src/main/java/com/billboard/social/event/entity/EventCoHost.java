package com.billboard.social.event.entity;
import com.billboard.social.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "event_cohosts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "user_id"}),
    indexes = {
        @Index(name = "idx_cohost_event", columnList = "event_id"),
        @Index(name = "idx_cohost_user", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventCoHost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "can_edit")
    @Builder.Default
    private Boolean canEdit = true;

    @Column(name = "can_invite")
    @Builder.Default
    private Boolean canInvite = true;

    @Column(name = "can_manage_rsvps")
    @Builder.Default
    private Boolean canManageRsvps = true;
}
