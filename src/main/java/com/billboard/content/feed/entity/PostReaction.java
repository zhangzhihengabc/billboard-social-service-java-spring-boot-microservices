package com.billboard.content.feed.entity;

import com.billboard.content.feed.entity.enums.ReactionType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "post_reactions", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}),
    indexes = {
        @Index(name = "idx_reaction_post", columnList = "post_id"),
        @Index(name = "idx_reaction_user", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostReaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 20)
    private ReactionType reactionType;
}
