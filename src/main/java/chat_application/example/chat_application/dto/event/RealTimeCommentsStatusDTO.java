package chat_application.example.chat_application.dto.event;

import chat_application.example.chat_application.dto.response.CommentResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealTimeCommentsStatusDTO {

    private String entityType;
    private Long entityId;
    private Boolean isEnabled;
    private Boolean hasNewComments;
    private Integer newCommentsCount;
    private List<CommentResponseDTO> newComments;
    private Long latestCommentId;
    private List<typingUserDTO> typingUsers;
    private LocalDateTime lastUpdated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class typingUserDTO {
        private Long userId;
        private String username;
        private String avatarUrl;
        private LocalDateTime startedTypingAt;
    }
}
