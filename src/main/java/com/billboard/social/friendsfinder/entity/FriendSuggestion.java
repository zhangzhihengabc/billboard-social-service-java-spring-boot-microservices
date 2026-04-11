package com.billboard.social.friendsfinder.entity;

import com.billboard.social.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "friend_suggestions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "suggested_user_id"}),
    indexes = {
        @Index(name = "idx_friend_suggestion_user", columnList = "user_id"),
        @Index(name = "idx_friend_suggestion_score", columnList = "suggestion_score")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FriendSuggestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "suggested_user_id", nullable = false)
    private Long suggestedUserId;

    @Column(name = "suggestion_score", nullable = false)
    @Builder.Default
    private Double suggestionScore = 0.0;

    /** Source of the suggestion: SCRIM_OPPONENT, ALGORITHMIC, MUTUAL_FRIENDS */
    @Column(name = "source", nullable = false, length = 30)
    private String source;

    @Column(name = "game_mode", length = 50)
    private String gameMode;

    @Column(name = "interaction_count")
    @Builder.Default
    private Integer interactionCount = 0;

    @Column(name = "mutual_friend_count")
    @Builder.Default
    private Integer mutualFriendCount = 0;

    @Column(name = "dismissed")
    @Builder.Default
    private Boolean dismissed = false;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;
}
