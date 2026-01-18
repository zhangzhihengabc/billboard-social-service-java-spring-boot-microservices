package com.billboard.social.graph.entity;
import com.billboard.social.common.entity.BaseEntity;

import com.billboard.social.graph.entity.enums.FriendshipStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "friendships", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}),
    indexes = {
        @Index(name = "idx_friendship_requester", columnList = "requester_id"),
        @Index(name = "idx_friendship_addressee", columnList = "addressee_id"),
        @Index(name = "idx_friendship_status", columnList = "status")
    }
)
@SQLDelete(sql = "UPDATE friendships SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friendship extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "addressee_id", nullable = false)
    private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "mutual_friends_count")
    @Builder.Default
    private Integer mutualFriendsCount = 0;

    public void accept() {
        this.status = FriendshipStatus.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
    }

    public void decline() {
        this.status = FriendshipStatus.DECLINED;
    }

    public void cancel() {
        this.status = FriendshipStatus.CANCELLED;
    }

    public boolean isPending() {
        return status == FriendshipStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == FriendshipStatus.ACCEPTED;
    }
}
