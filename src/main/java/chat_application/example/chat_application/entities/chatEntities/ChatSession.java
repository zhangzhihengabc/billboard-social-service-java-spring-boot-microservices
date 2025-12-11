package chat_application.example.chat_application.entities.chatEntities;


import chat_application.example.chat_application.entities.GroupMessageRoom;
import chat_application.example.chat_application.entities.User;
import chat_application.example.chat_application.entities.enums.PresenceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_sessions", indexes = {
    @Index(name = "idx_session_user", columnList = "user_id"),
    @Index(name = "idx_session_room", columnList = "room_id"),
    @Index(name = "idx_session_active", columnList = "is_active"),
    @Index(name = "idx_session_last_activity", columnList = "last_activity_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private GroupMessageRoom room;

    // For 1-on-1 chats
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_partner_id")
    private User chatPartner;

    @Column(name = "session_token", unique = true)
    private String sessionToken;

    @Column(name = "websocket_id")
    private String websocketId;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_typing")
    @Builder.Default
    private Boolean isTyping = false;

    @Column(name = "typing_started_at")
    private LocalDateTime typingStartedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "presence_status")
    @Builder.Default
    private PresenceStatus presenceStatus = PresenceStatus.ONLINE;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Column(name = "device_type")
    private String deviceType; // web, mobile, desktop

    @Column(name = "ip_address")
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "connected_at", updatable = false)
    private LocalDateTime connectedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper methods
    public void startTyping() {
        this.isTyping = true;
        this.typingStartedAt = LocalDateTime.now();
    }

    public void stopTyping() {
        this.isTyping = false;
        this.typingStartedAt = null;
    }

    public void updateActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }

    public void disconnect() {
        this.isActive = false;
        this.disconnectedAt = LocalDateTime.now();
        this.presenceStatus = PresenceStatus.OFFLINE;
        this.isTyping = false;
    }
}
