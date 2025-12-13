package chat_application.example.chat_application.dto.request.message;

import chat_application.example.chat_application.entities.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequestDTO {

    private Long senderId;
    private Long roomId;
    private String content;

    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    // Attachment
    private String attachmentUrl;
    private String attachmentType;  // image, video, audio, file
    private String attachmentName;
    private Long attachmentSize;

    // Reply
    private Long replyToId;

    // System message flag
    @Builder.Default
    private Boolean isSystemMessage = false;
}
