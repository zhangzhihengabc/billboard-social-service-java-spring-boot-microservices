package com.billboard.content.forum.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_post_topic", columnList = "topic_id"),
    @Index(name = "idx_post_author", columnList = "author_id"),
    @Index(name = "idx_post_parent", columnList = "parent_id"),
    @Index(name = "idx_post_created", columnList = "created_at")
})
@SQLDelete(sql = "UPDATE posts SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Post parent;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "upvote_count")
    @Builder.Default
    private Integer upvoteCount = 0;

    @Column(name = "downvote_count")
    @Builder.Default
    private Integer downvoteCount = 0;

    @Column(name = "score")
    @Builder.Default
    private Integer score = 0;

    @Column(name = "reply_count")
    @Builder.Default
    private Integer replyCount = 0;

    @Column(name = "is_solution")
    @Builder.Default
    private Boolean isSolution = false;

    @Column(name = "is_edited")
    @Builder.Default
    private Boolean isEdited = false;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "is_hidden")
    @Builder.Default
    private Boolean isHidden = false;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public void incrementReplyCount() {
        this.replyCount++;
    }

    public void decrementReplyCount() {
        if (this.replyCount > 0) this.replyCount--;
    }

    public void updateScore() {
        this.score = this.upvoteCount - this.downvoteCount;
    }

    public void markAsEdited(UUID editorId) {
        this.isEdited = true;
        this.editedAt = LocalDateTime.now();
        this.editedBy = editorId;
    }
}
