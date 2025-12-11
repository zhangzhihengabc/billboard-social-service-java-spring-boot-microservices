package chat_application.example.chat_application.dto.response;

import chat_application.example.chat_application.dto.userSummaryDTO;
import chat_application.example.chat_application.entities.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class messageResponseDTO {

    private Long id;
    private Long roomId;
    private userSummaryDTO sender;
    private String content;
    private MessageType messageType;

    // Attachment
    private String attachmentUrl;
    private String attachmentType;
    private String attachmentName;
    private Long attachmentSize;

    // Reply
    private Long replyToId;
    private String replyToContent;
    private String replyToSenderName;

    // Reactions
    private Integer likeCount;

    // Metadata
    private Boolean isEdited;
    private LocalDateTime editedAt;
    private Boolean isDeleted;
    private Boolean isPinned;
    private LocalDateTime pinnedAt;
    private Boolean isSystemMessage;

    private LocalDateTime createdAt;
}
