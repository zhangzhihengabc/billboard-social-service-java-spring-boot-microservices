package chat_application.example.chat_application.dto.request.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentAttachmentRequestDTO {

    @NotBlank(message = "Attachment URL is required")
    @Size(max = 500, message = "Attachment URL must not exceed 500 characters")
    private String url;

    @NotBlank(message = "Attachment type is required")
    @Size(max = 50, message = "Attachment type must not exceed 50 characters")
    private String type;  // image, video, audio, document, etc.

}
