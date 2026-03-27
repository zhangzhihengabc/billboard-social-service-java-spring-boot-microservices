package com.billboard.social.gamegroup.entity;

import com.billboard.social.common.entity.BaseEntity;
import com.billboard.social.group.entity.Group;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "group_team_links",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "team_id"}),
        indexes = {
                @Index(name = "idx_group_team_group", columnList = "group_id"),
                @Index(name = "idx_group_team_team", columnList = "team_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupTeamLink extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "linked_by", nullable = false)
    private Long linkedBy;

    @Column(name = "linked_at", nullable = false)
    @Builder.Default
    private LocalDateTime linkedAt = LocalDateTime.now();
}