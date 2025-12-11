package chat_application.example.chat_application.entities.commentEntities;

import chat_application.example.chat_application.entities.Group;
import chat_application.example.chat_application.entities.User;
import chat_application.example.chat_application.entities.enums.InviteStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class GroupInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id", nullable = false)
    private User invitee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InviteStatus status = InviteStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void accept() {
        this.status = InviteStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void decline() {
        this.status = InviteStatus.DECLINED;
        this.respondedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = InviteStatus.CANCELLED;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
