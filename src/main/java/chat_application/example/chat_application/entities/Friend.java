package chat_application.example.chat_application.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "friends",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "friend_id"}),
        indexes = {
                @Index(name = "idx_friend_user", columnList = "user_id"),
                @Index(name = "idx_friend_friend", columnList = "friend_id"),
                @Index(name = "idx_friend_status", columnList = "status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user who initiated the friend request
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The user who received the friend request
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    /**
     * Status of the friendship
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /**
     * When the friend request was accepted
     */
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    /**
     * When the friendship was blocked (if blocked)
     */
    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    /**
     * Who blocked (if blocked) - could be either user or friend
     */
    @Column(name = "blocked_by")
    private Long blockedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Accept the friend request
     */
    public void accept() {
        this.status = "ACCEPTED";
        this.acceptedAt = LocalDateTime.now();
    }

    /**
     * Decline the friend request
     */
    public void decline() {
        this.status = "DECLINED";
    }

    /**
     * Block the friendship
     */
    public void block(Long blockedByUserId) {
        this.status = "BLOCKED";
        this.blockedAt = LocalDateTime.now();
        this.blockedBy = blockedByUserId;
    }

    /**
     * Unblock the friendship (reverts to ACCEPTED if was previously accepted)
     */
    public void unblock() {
        if ("BLOCKED".equals(this.status)) {
            this.status = this.acceptedAt != null ? "ACCEPTED" : "PENDING";
            this.blockedAt = null;
            this.blockedBy = null;
        }
    }

    /**
     * Check if friendship is active
     */
    public boolean isActive() {
        return "ACCEPTED".equals(this.status);
    }

    /**
     * Check if friendship is pending
     */
    public boolean isPending() {
        return "PENDING".equals(this.status);
    }

    /**
     * Check if friendship is blocked
     */
    public boolean isBlocked() {
        return "BLOCKED".equals(this.status);
    }

    /**
     * Get the other user in the friendship
     */
    public User getOtherUser(Long currentUserId) {
        if (user.getId().equals(currentUserId)) {
            return friend;
        }
        return user;
    }
}
