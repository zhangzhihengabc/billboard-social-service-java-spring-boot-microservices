package chat_application.example.chat_application.entities;

import chat_application.example.chat_application.entities.enums.MemberRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * GroupMessageMember Entity - Room Membership
 * Tracks user membership in chat rooms with roles
 */
@Entity
@Table(name = "group_message_members", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}),
    indexes = {
        @Index(name = "idx_member_room", columnList = "room_id"),
        @Index(name = "idx_member_user", columnList = "user_id"),
        @Index(name = "idx_member_role", columnList = "role")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessageMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private GroupMessageRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "is_muted")
    @Builder.Default
    private Boolean isMuted = false;

    @Column(name = "muted_until")
    private LocalDateTime mutedUntil;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Column(name = "unread_count")
    @Builder.Default
    private Integer unreadCount = 0;

    @Column(name = "notifications_enabled")
    @Builder.Default
    private Boolean notificationsEnabled = true;

    @Column(name = "invited_by_id")
    private Long invitedById;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;

    // Helper methods
    public boolean canManageMembers() {
        return role == MemberRole.OWNER || role == MemberRole.ADMIN;
    }

    public boolean canDeleteMessages() {
        return role != MemberRole.MEMBER;
    }

    public boolean canMuteMembers() {
        return role == MemberRole.OWNER || role == MemberRole.ADMIN || role == MemberRole.MODERATOR;
    }

    public void incrementUnread() {
        this.unreadCount++;
    }

    public void markAsRead(Long messageId) {
        this.lastReadMessageId = messageId;
        this.lastReadAt = LocalDateTime.now();
        this.unreadCount = 0;
    }
}
