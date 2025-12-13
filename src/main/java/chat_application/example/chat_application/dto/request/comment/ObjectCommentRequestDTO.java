package chat_application.example.chat_application.dto.request.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectCommentRequestDTO {

    @NotBlank(message = "Entity type is required")
    @Size(max = 50, message = "Entity type must not exceed 50 characters")
    private String entityType;  // video, replay, album, playlist, etc.

    @NotNull(message = "Entity ID is required")
    private Long entityId;

    @NotBlank(message = "Object type is required")
    @Size(max = 50, message = "Object type must not exceed 50 characters")
    private String objectType;

    @NotNull(message = "Object ID is required")
    private Long objectId;

    @NotBlank(message = "Comment content is required")
    @Size(max = 5000, message = "Comment must not exceed 5000 characters")
    private String content;

    // For reply to another comment
    private Long parentId;

    // For video/replay comments - timestamp in seconds
    private Integer timestampSeconds;

    // Mentioned user IDs
    private String mentionedUserIds;
    private List<Long> mentionedUserIdList;

    // Optional embed data
    private CreateCommentRequestDTO.EmbedData embed;

    // Optional attachment data
    private CreateCommentRequestDTO.AttachmentData attachment;
}
