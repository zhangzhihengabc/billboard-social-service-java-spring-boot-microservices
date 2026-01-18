package com.billboard.social.graph.entity;
import com.billboard.social.common.entity.BaseEntity;

import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.entity.enums.ReactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "reactions", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "content_type", "content_id"}),
    indexes = {
        @Index(name = "idx_reaction_user", columnList = "user_id"),
        @Index(name = "idx_reaction_content", columnList = "content_type, content_id"),
        @Index(name = "idx_reaction_type", columnList = "reaction_type")
    }
)
@SQLDelete(sql = "UPDATE reactions SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "content_owner_id")
    private UUID contentOwnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 20)
    @Builder.Default
    private ReactionType reactionType = ReactionType.LIKE;

    public void changeReaction(ReactionType newType) {
        this.reactionType = newType;
    }
}
