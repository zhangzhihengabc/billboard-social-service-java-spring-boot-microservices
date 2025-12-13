package chat_application.example.chat_application.dto.response;

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
public class CommentResponseDTO {

    private Long id;
    private String content;
    private String entityType;
    private Long entityId;
    private String objectType;
    private Long objectId;
    private Long parentId;

    private Integer timestampSeconds;

    private EmbedDTO embed;

    private AttachmentDTO attachment;

    private String mentionedUserIds;
    private List<Long> mentionedUserIdList;

    private Integer likeCount;
    private Integer replyCount;

    private Boolean isPinned;
    private Boolean isHighlighted;
    private Boolean isEdited;
    private LocalDateTime editedAt;
    private Boolean isDeleted;
    private LocalDateTime deletedAt;

    private Boolean isLikedByCurrentUser;
    private Boolean isOwnComment;

    private OwnerDTO owner;

    private List<CommentResponseDTO> replies;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnerDTO {
        private Long id;
        private String name;
        private String email;
        private String profilePicture;
        private String phoneNumber;
        private String gender;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbedDTO {
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
    public static class AttachmentDTO {
        private String url;
        private String type;
    }
}
