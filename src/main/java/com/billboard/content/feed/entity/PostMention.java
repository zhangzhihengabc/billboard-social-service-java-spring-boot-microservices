package com.billboard.content.feed.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "post_mentions", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "mentioned_user_id"}),
    indexes = {
        @Index(name = "idx_mention_post", columnList = "post_id"),
        @Index(name = "idx_mention_user", columnList = "mentioned_user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostMention extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "mentioned_user_id", nullable = false)
    private UUID mentionedUserId;

    @Column(name = "position_start")
    private Integer positionStart;

    @Column(name = "position_end")
    private Integer positionEnd;
}
