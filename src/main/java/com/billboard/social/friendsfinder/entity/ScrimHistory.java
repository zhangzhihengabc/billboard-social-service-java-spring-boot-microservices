package com.billboard.social.friendsfinder.entity;

import com.billboard.social.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scrim_history", indexes = {
    @Index(name = "idx_scrim_history_user_a", columnList = "user_id_a"),
    @Index(name = "idx_scrim_history_user_b", columnList = "user_id_b"),
    @Index(name = "idx_scrim_history_pair", columnList = "user_id_a, user_id_b"),
    @Index(name = "idx_scrim_history_game_mode", columnList = "game_mode")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScrimHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id_a", nullable = false)
    private Long userIdA;

    @Column(name = "user_id_b", nullable = false)
    private Long userIdB;

    @Column(name = "esports_match_id")
    private Long esportsMatchId;

    @Column(name = "game_mode", length = 50)
    private String gameMode;

    @Column(name = "match_quality_score")
    private Double matchQualityScore;

    @Column(name = "played_at")
    private LocalDateTime playedAt;
}
