package com.ossn.content.forum.entity;

import com.ossn.content.forum.entity.enums.ForumType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "forums", indexes = {
    @Index(name = "idx_forum_slug", columnList = "slug"),
    @Index(name = "idx_forum_type", columnList = "forum_type"),
    @Index(name = "idx_forum_parent", columnList = "parent_id"),
    @Index(name = "idx_forum_group", columnList = "group_id")
})
@SQLDelete(sql = "UPDATE forums SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Forum extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Forum parent;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "forum_type", nullable = false, length = 20)
    @Builder.Default
    private ForumType forumType = ForumType.GENERAL;

    @Column(name = "icon", length = 100)
    private String icon;

    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "topic_count")
    @Builder.Default
    private Integer topicCount = 0;

    @Column(name = "post_count")
    @Builder.Default
    private Integer postCount = 0;

    @Column(name = "is_locked")
    @Builder.Default
    private Boolean isLocked = false;

    @Column(name = "requires_approval")
    @Builder.Default
    private Boolean requiresApproval = false;

    @Column(name = "min_level_to_post")
    @Builder.Default
    private Integer minLevelToPost = 0;

    @Column(name = "last_topic_id")
    private UUID lastTopicId;

    @Column(name = "last_post_at")
    private java.time.LocalDateTime lastPostAt;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Forum> subForums = new ArrayList<>();

    public void incrementTopicCount() {
        this.topicCount++;
    }

    public void decrementTopicCount() {
        if (this.topicCount > 0) this.topicCount--;
    }

    public void incrementPostCount() {
        this.postCount++;
    }

    public void decrementPostCount() {
        if (this.postCount > 0) this.postCount--;
    }
}
