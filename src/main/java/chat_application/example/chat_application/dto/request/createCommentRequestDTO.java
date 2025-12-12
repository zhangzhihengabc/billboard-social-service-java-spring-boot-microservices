package chat_application.example.chat_application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class createCommentRequestDTO {

    @NotBlank(message = "Comment content is required")
    @Size(max = 5000, message = "Comment must not exceed 5000 characters")
    private String content;

    private Long parentId;

    private Integer timestampSeconds;

    private String mentionedUserIds;
    private List<Long> mentionedUserIdList;

    private EmbedData embed;

    private AttachmentData attachment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbedData {
        private String type;
        private String url;
        private String title;
        private String description;
        private String thumbnail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentData {
        private String url;
        private String type;
    }
}
