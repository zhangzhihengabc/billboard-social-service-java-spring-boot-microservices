package com.ossn.content.feed.entity;

import com.ossn.content.feed.entity.enums.PostType;
import com.ossn.content.feed.entity.enums.PostVisibility;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_post_author", columnList = "author_id"),
    @Index(name = "idx_post_wall_owner", columnList = "wall_owner_id"),
    @Index(name = "idx_post_group", columnList = "group_id"),
    @Index(name = "idx_post_visibility", columnList = "visibility"),
    @Index(name = "idx_post_created", columnList = "created_at DESC")
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

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "wall_owner_id")
    private UUID wallOwnerId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "event_id")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 20)
    @Builder.Default
    private PostType postType = PostType.STATUS;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 30)
    @Builder.Default
    private PostVisibility visibility = PostVisibility.PUBLIC;

    @Column(name = "content", length = 10000)
    private String content;

    @Column(name = "link_url", length = 1000)
    private String linkUrl;

    @Column(name = "link_title", length = 255)
    private String linkTitle;

    @Column(name = "link_description", length = 500)
    private String linkDescription;

    @Column(name = "link_image", length = 500)
    private String linkImage;

    @Column(name = "shared_post_id")
    private UUID sharedPostId;

    @Column(name = "is_pinned")
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "is_highlighted")
    @Builder.Default
    private Boolean isHighlighted = false;

    @Column(name = "allow_comments")
    @Builder.Default
    private Boolean allowComments = true;

    @Column(name = "allow_reactions")
    @Builder.Default
    private Boolean allowReactions = true;

    // Counts (denormalized for performance)
    @Column(name = "like_count")
    @Builder.Default
    private Integer likeCount = 0;

    @Column(name = "love_count")
    @Builder.Default
    private Integer loveCount = 0;

    @Column(name = "comment_count")
    @Builder.Default
    private Integer commentCount = 0;

    @Column(name = "share_count")
    @Builder.Default
    private Integer shareCount = 0;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "feeling", length = 50)
    private String feeling;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostMedia> mediaItems = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostMention> mentions = new ArrayList<>();

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) this.likeCount--;
    }

    public void incrementLoveCount() {
        this.loveCount++;
    }

    public void decrementLoveCount() {
        if (this.loveCount > 0) this.loveCount--;
    }

    public void incrementCommentCount() {
        this.commentCount++;
    }

    public void decrementCommentCount() {
        if (this.commentCount > 0) this.commentCount--;
    }

    public void incrementShareCount() {
        this.shareCount++;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public boolean isWallPost() {
        return wallOwnerId != null && !wallOwnerId.equals(authorId);
    }

    public boolean isGroupPost() {
        return groupId != null;
    }

    public boolean isSharedPost() {
        return sharedPostId != null;
    }

    public int getTotalReactionCount() {
        return likeCount + loveCount;
    }
}
