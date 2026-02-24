package com.billboard.social.graph.entity;
import com.billboard.social.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "follows", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "following_id"}),
    indexes = {
        @Index(name = "idx_follow_follower", columnList = "follower_id"),
        @Index(name = "idx_follow_following", columnList = "following_id")
    }
)
@SQLDelete(sql = "UPDATE follows SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Follow extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "follower_id", nullable = false)
    private Long followerId;

    @Column(name = "following_id", nullable = false)
    private Long followingId;

    @Column(name = "notifications_enabled")
    @Builder.Default
    private Boolean notificationsEnabled = true;

    @Column(name = "is_close_friend")
    @Builder.Default
    private Boolean isCloseFriend = false;

    @Column(name = "is_muted")
    @Builder.Default
    private Boolean isMuted = false;

    public void enableNotifications() {
        this.notificationsEnabled = true;
    }

    public void disableNotifications() {
        this.notificationsEnabled = false;
    }

    public void mute() {
        this.isMuted = true;
    }

    public void unmute() {
        this.isMuted = false;
    }

    public void markAsCloseFriend() {
        this.isCloseFriend = true;
    }

    public void unmarkAsCloseFriend() {
        this.isCloseFriend = false;
    }
}
