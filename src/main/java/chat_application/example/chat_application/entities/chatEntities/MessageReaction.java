package chat_application.example.chat_application.entities.chatEntities;


import chat_application.example.chat_application.entities.GroupMessage;
import chat_application.example.chat_application.entities.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_reactions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id", "reaction_type"}),
    indexes = {
        @Index(name = "idx_reaction_message", columnList = "message_id"),
        @Index(name = "idx_reaction_user", columnList = "user_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private GroupMessage message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reaction_type", nullable = false, length = 50)
    private String reactionType; // like, love, laugh, etc. or emoji code

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
