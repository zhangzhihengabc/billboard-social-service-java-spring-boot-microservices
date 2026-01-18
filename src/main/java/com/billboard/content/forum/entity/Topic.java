package com.billboard.content.forum.entity;

import com.billboard.content.forum.entity.enums.TopicStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "topics", indexes = {
    @Index(name = "idx_topic_forum", columnList = "forum_id"),
    @Index(name = "idx_topic_author", columnList = "author_id"),
    @Index(name = "idx_topic_status", columnList = "status"),
    @Index(name = "idx_topic_pinned", columnList = "is_pinned"),
    @Index(name = "idx_topic_last_post", columnList = "last_post_at DESC")
})
@SQLDelete(sql = "UPDATE topics SET deleted_at = NOW(), status = 'HIDDEN' WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Topic extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forum_id", nullable = false)
    private Forum forum;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "slug", nullable = false, length = 220)
    private String slug;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TopicStatus status = TopicStatus.OPEN;

    @Column(name = "is_pinned")
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "is_sticky")
    @Builder.Default
    private Boolean isSticky = false;

    @Column(name = "is_announcement")
    @Builder.Default
    private Boolean isAnnouncement = false;

    @Column(name = "is_featured")
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(name = "reply_count")
    @Builder.Default
    private Integer replyCount = 0;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "upvote_count")
    @Builder.Default
    private Integer upvoteCount = 0;

    @Column(name = "downvote_count")
    @Builder.Default
    private Integer downvoteCount = 0;

    @Column(name = "score")
    @Builder.Default
    private Integer score = 0;

    @Column(name = "last_post_id")
    private UUID lastPostId;

    @Column(name = "last_post_author_id")
    private UUID lastPostAuthorId;

    @Column(name = "last_post_at")
    private LocalDateTime lastPostAt;

    @Column(name = "is_edited")
    @Builder.Default
    private Boolean isEdited = false;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "lock_reason", length = 500)
    private String lockReason;

    @Column(name = "tags", length = 500)
    private String tags;

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Post> posts = new ArrayList<>();

    public void incrementReplyCount() {
        this.replyCount++;
    }

    public void decrementReplyCount() {
        if (this.replyCount > 0) this.replyCount--;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void updateScore() {
        this.score = this.upvoteCount - this.downvoteCount;
    }

    public boolean isOpen() {
        return status == TopicStatus.OPEN;
    }

    public boolean isLocked() {
        return status == TopicStatus.LOCKED || status == TopicStatus.CLOSED;
    }
}
