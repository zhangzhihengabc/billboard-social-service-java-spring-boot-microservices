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
public class WallPostResponseDTO {

    private Long id;
    private userSummaryDTO owner;
    private String content;
    private String postType;
    private String privacy;
    private String location;

    private userSummaryDTO targetUser;
    private groupSummaryDTO targetGroup;

    private embedInfoDTO embed;

    private originalPostDTO originalPost;

    private List<Long> taggedUserIds;
    private List<userSummaryDTO> taggedUsers;

    private Integer likeCount;
    private Integer commentCount;
    private Long shareCount;

    private Boolean isLikedByCurrentUser;
    private Boolean isOwnPost;

    private Boolean isEdited;
    private LocalDateTime editedAt;
    private Boolean isPinned;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class userSummaryDTO {
        private Long id;
        private String name;
        private String email;
        private String profilePhotoUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class groupSummaryDTO {
        private Long id;
        private String name;
        private String iconUrl;
        private String privacy;
        private Integer memberCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class embedInfoDTO {
        private String url;
        private String type;
        private String title;
        private String description;
        private String thumbnail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class originalPostDTO {
        private Long id;
        private userSummaryDTO owner;
        private String content;
        private String postType;
        private Integer likeCount;
        private Integer commentCount;
        private LocalDateTime createdAt;
    }
}
