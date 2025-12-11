package chat_application.example.chat_application.entities;

import chat_application.example.chat_application.entities.enums.MessageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * GroupMessage Entity - Chat Message
 * Represents a message in a group chat room
 */
@Entity
@Table(name = "group_messages", indexes = {
    @Index(name = "idx_gm_room", columnList = "room_id"),
    @Index(name = "idx_gm_sender", columnList = "sender_id"),
    @Index(name = "idx_gm_created", columnList = "created_at"),
    @Index(name = "idx_gm_reply_to", columnList = "reply_to_id"),
    @Index(name = "idx_gm_deleted", columnList = "is_deleted")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private GroupMessageRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    // Attachment support
    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "attachment_type")
    private String attachmentType; // image, video, audio, file

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    // Reply/thread support
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private GroupMessage replyTo;

    @OneToMany(mappedBy = "replyTo", cascade = CascadeType.ALL)
    @Builder.Default
    private List<GroupMessage> replies = new ArrayList<>();

    // Reactions (simplified - just counts)
    @Column(name = "like_count")
    @Builder.Default
    private Integer likeCount = 0;

    // Message metadata
    @Column(name = "is_edited")
    @Builder.Default
    private Boolean isEdited = false;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by_id")
    private Long deletedById;

    @Column(name = "is_pinned")
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;

    @Column(name = "pinned_by_id")
    private Long pinnedById;

    // System message support
    @Column(name = "is_system_message")
    @Builder.Default
    private Boolean isSystemMessage = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper methods
    public void softDelete(Long deletedBy) {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedById = deletedBy;
        this.content = "[Message deleted]";
    }

    public void edit(String newContent) {
        this.content = newContent;
        this.isEdited = true;
        this.editedAt = LocalDateTime.now();
    }

    public void pin(Long pinnedBy) {
        this.isPinned = true;
        this.pinnedAt = LocalDateTime.now();
        this.pinnedById = pinnedBy;
    }

    public void unpin() {
        this.isPinned = false;
        this.pinnedAt = null;
        this.pinnedById = null;
    }

    public void incrementLikes() {
        this.likeCount++;
    }

    public void decrementLikes() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }
}
