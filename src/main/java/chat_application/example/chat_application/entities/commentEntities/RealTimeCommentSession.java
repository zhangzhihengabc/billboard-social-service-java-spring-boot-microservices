package chat_application.example.chat_application.entities.commentEntities;

import chat_application.example.chat_application.entities.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "realtime_comment_sessions", indexes = {
    @Index(name = "idx_rtc_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_rtc_user", columnList = "user_id"),
    @Index(name = "idx_rtc_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RealTimeCommentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "session_token")
    private String sessionToken;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "realtime_enabled")
    @Builder.Default
    private Boolean realtimeEnabled = true;

    @Column(name = "last_comment_id")
    private Long lastCommentId;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
