package chat_application.example.chat_application.entities.commentEntities;

import chat_application.example.chat_application.entities.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comments", indexes = {
    @Index(name = "idx_comment_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_comment_owner", columnList = "owner_id"),
    @Index(name = "idx_comment_parent", columnList = "parent_id"),
    @Index(name = "idx_comment_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Polymorphic association - can comment on any entity type
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType; // post, video, photo, audio, replay, etc.

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    // Optional: specific object within entity (e.g., timestamp in video)
    @Column(name = "object_type", length = 50)
    private String objectType;

    @Column(name = "object_id")
    private Long objectId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // Nested comments (replies)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Comment> replies = new ArrayList<>();

    // For video replay comments - timestamp in seconds
    @Column(name = "timestamp_seconds")
    private Integer timestampSeconds;

    // Embedded content
    @Column(name = "embed_url")
    private String embedUrl;

    @Column(name = "embed_type")
    private String embedType; // link, image, video, gif

    @Column(name = "embed_title")
    private String embedTitle;

    @Column(name = "embed_description")
    private String embedDescription;

    @Column(name = "embed_thumbnail")
    private String embedThumbnail;

    // Attachment support
    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "attachment_type")
    private String attachmentType;

    // Stats
    @Column(name = "like_count")
    @Builder.Default
    private Integer likeCount = 0;

    @Column(name = "reply_count")
    @Builder.Default
    private Integer replyCount = 0;

    // Status
    @Column(name = "is_edited")
    @Builder.Default
    private Boolean isEdited = false;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "is_pinned")
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "is_highlighted")
    @Builder.Default
    private Boolean isHighlighted = false;

    // Mentioned users (stored as comma-separated IDs)
    @Column(name = "mentioned_user_ids")
    private String mentionedUserIds;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper methods
    public void edit(String newContent) {
        this.content = newContent;
        this.isEdited = true;
        this.editedAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.content = "[Comment deleted]";
    }

    public void incrementLikes() {
        this.likeCount++;
    }

    public void decrementLikes() {
        if (this.likeCount > 0) this.likeCount--;
    }

    public void incrementReplies() {
        this.replyCount++;
    }

    public void decrementReplies() {
        if (this.replyCount > 0) this.replyCount--;
    }
}
