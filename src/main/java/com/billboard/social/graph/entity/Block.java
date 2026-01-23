package com.billboard.social.graph.entity;
import com.billboard.social.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "blocks", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}),
    indexes = {
        @Index(name = "idx_block_blocker", columnList = "blocker_id"),
        @Index(name = "idx_block_blocked", columnList = "blocked_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Block extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "hide_from_suggestions")
    @Builder.Default
    private Boolean hideFromSuggestions = true;

    @Column(name = "block_messages")
    @Builder.Default
    private Boolean blockMessages = true;

    @Column(name = "block_posts")
    @Builder.Default
    private Boolean blockPosts = true;

    @Column(name = "block_comments")
    @Builder.Default
    private Boolean blockComments = true;
}
