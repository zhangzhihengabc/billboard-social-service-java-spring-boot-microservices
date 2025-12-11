package chat_application.example.chat_application.entities.chatEntities;



import chat_application.example.chat_application.entities.Group;
import chat_application.example.chat_application.entities.User;
import chat_application.example.chat_application.entities.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_member_requests",
    uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}),
    indexes = {
        @Index(name = "idx_request_group", columnList = "group_id"),
        @Index(name = "idx_request_user", columnList = "user_id"),
        @Index(name = "idx_request_status", columnList = "status")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String message; // Optional message from requester

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_id")
    private User processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void approve(User approvedBy) {
        this.status = RequestStatus.APPROVED;
        this.processedBy = approvedBy;
        this.processedAt = LocalDateTime.now();
    }

    public void decline(User declinedBy, String reason) {
        this.status = RequestStatus.DECLINED;
        this.processedBy = declinedBy;
        this.processedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    public void cancel() {
        this.status = RequestStatus.CANCELLED;
        this.processedAt = LocalDateTime.now();
    }
}
