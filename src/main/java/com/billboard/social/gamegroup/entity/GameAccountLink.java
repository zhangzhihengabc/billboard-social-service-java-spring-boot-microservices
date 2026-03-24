package com.billboard.social.gamegroup.entity;

import com.billboard.social.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "game_account_links",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_tag", "game_account_id"}),
        indexes = {
                @Index(name = "idx_game_account_user", columnList = "user_id"),
                @Index(name = "idx_game_account_game_tag", columnList = "game_tag")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameAccountLink extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "game_tag", nullable = false, length = 50)
    private String gameTag;

    @Column(name = "game_account_id", nullable = false, length = 100)
    private String gameAccountId;

    @Column(name = "game_account_name", length = 100)
    private String gameAccountName;

    @Column(name = "verification_status", nullable = false, length = 20)
    @Builder.Default
    private String verificationStatus = "PENDING";

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
}