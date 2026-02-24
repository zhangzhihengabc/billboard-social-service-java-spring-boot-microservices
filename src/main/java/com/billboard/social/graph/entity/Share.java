package com.billboard.social.graph.entity;
import com.billboard.social.common.entity.BaseEntity;

import com.billboard.social.graph.entity.enums.ContentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "shares", indexes = {
    @Index(name = "idx_share_user", columnList = "user_id"),
    @Index(name = "idx_share_content", columnList = "content_type, content_id"),
    @Index(name = "idx_share_target", columnList = "target_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Share extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "content_owner_id")
    private Long contentOwnerId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "share_to_feed")
    @Builder.Default
    private Boolean shareToFeed = true;

    @Column(name = "share_to_story")
    @Builder.Default
    private Boolean shareToStory = false;

    @Column(name = "is_private_share")
    @Builder.Default
    private Boolean isPrivateShare = false;
}
