package chat_application.example.chat_application.entities;

import chat_application.example.chat_application.entities.group.Group;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_post_owner", columnList = "owner_id"),
        @Index(name = "idx_post_target_user", columnList = "target_user_id"),
        @Index(name = "idx_post_target_group", columnList = "target_group_id"),
        @Index(name = "idx_post_type", columnList = "post_type"),
        @Index(name = "idx_post_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // For posts on another user's wall
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    // For posts in groups
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_group_id")
    private Group targetGroup;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "post_type", length = 20)
    @Builder.Default
    private String postType = "home"; // home, user, group, share

    @Column(length = 20)
    @Builder.Default
    private String privacy = "PUBLIC"; // PUBLIC, FRIENDS, PRIVATE, GROUP

    @Column(length = 255)
    private String location;

    // Embed content
    @Column(name = "embed_url", length = 500)
    private String embedUrl;

    @Column(name = "embed_type", length = 50)
    private String embedType;

    @Column(name = "embed_title", length = 255)
    private String embedTitle;

    @Column(name = "embed_description", length = 500)
    private String embedDescription;

    @Column(name = "embed_thumbnail", length = 500)
    private String embedThumbnail;

    // For shared posts
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_post_id")
    private Post originalPost;

    // Tagged users (comma-separated IDs)
    @Column(name = "tagged_user_ids")
    private String taggedUserIds;

    // Stats
    @Column(name = "likes_count")
    @Builder.Default
    private Integer likesCount = 0;

    @Column(name = "comments_count")
    @Builder.Default
    private Integer commentsCount = 0;

    @Column(name = "shares_count")
    @Builder.Default
    private Integer sharesCount = 0;

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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper methods
    public void incrementLikes() {
        this.likesCount = (this.likesCount == null ? 0 : this.likesCount) + 1;
    }

    public void decrementLikes() {
        this.likesCount = Math.max(0, (this.likesCount == null ? 0 : this.likesCount) - 1);
    }

    public void incrementComments() {
        this.commentsCount = (this.commentsCount == null ? 0 : this.commentsCount) + 1;
    }

    public void decrementComments() {
        this.commentsCount = Math.max(0, (this.commentsCount == null ? 0 : this.commentsCount) - 1);
    }

    public void incrementShares() {
        this.sharesCount = (this.sharesCount == null ? 0 : this.sharesCount) + 1;
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
