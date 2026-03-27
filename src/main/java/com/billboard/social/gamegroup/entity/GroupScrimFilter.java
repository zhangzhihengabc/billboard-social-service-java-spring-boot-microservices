package com.billboard.social.gamegroup.entity;

import com.billboard.social.common.entity.BaseEntity;
import com.billboard.social.group.entity.Group;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "group_scrim_filters", indexes = {
        @Index(name = "idx_scrim_filter_group", columnList = "group_id"),
        @Index(name = "idx_scrim_filter_search", columnList = "game_tag, region, is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupScrimFilter extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "game_tag", nullable = false, length = 50)
    private String gameTag;

    @Column(name = "region", length = 30)
    private String region;

    @Column(name = "format", length = 10)
    private String format;

    @Column(name = "map_pool", columnDefinition = "TEXT")
    private String mapPool;

    @Column(name = "min_team_size")
    private Integer minTeamSize;

    @Column(name = "max_team_size")
    private Integer maxTeamSize;

    @Column(name = "min_elo")
    private Integer minElo;

    @Column(name = "max_elo")
    private Integer maxElo;

    @Column(name = "availability_slots", columnDefinition = "TEXT")
    private String availabilitySlots;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = false;

    @Column(name = "last_broadcast_at")
    private LocalDateTime lastBroadcastAt;
}