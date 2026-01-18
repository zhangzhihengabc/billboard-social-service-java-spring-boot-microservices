package com.billboard.content.feed.dto.request;

import com.billboard.content.feed.entity.enums.PostType;
import com.billboard.content.feed.entity.enums.PostVisibility;
import com.billboard.content.feed.entity.enums.ReactionType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class FeedRequests {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostRequest {
        @Size(max = 10000, message = "Content cannot exceed 10000 characters")
        private String content;

        @Builder.Default
        private PostType postType = PostType.STATUS;

        @Builder.Default
        private PostVisibility visibility = PostVisibility.PUBLIC;

        private UUID wallOwnerId;
        private UUID groupId;
        private UUID eventId;

        private String linkUrl;

        private List<MediaItem> mediaItems;
        private List<UUID> mentionedUserIds;

        private String feeling;
        private String location;

        private LocalDateTime scheduledAt;

        @Builder.Default
        private Boolean allowComments = true;

        @Builder.Default
        private Boolean allowReactions = true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePostRequest {
        @Size(max = 10000)
        private String content;

        private PostVisibility visibility;

        private String feeling;
        private String location;

        private Boolean allowComments;
        private Boolean allowReactions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharePostRequest {
        @NotNull(message = "Original post ID is required")
        private UUID originalPostId;

        @Size(max = 1000)
        private String comment;

        @Builder.Default
        private PostVisibility visibility = PostVisibility.PUBLIC;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCommentRequest {
        @NotBlank(message = "Comment content is required")
        @Size(max = 2000, message = "Comment cannot exceed 2000 characters")
        private String content;

        private UUID parentId;

        private String mediaUrl;
        private String mediaType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateCommentRequest {
        @NotBlank(message = "Comment content is required")
        @Size(max = 2000)
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactRequest {
        @NotNull(message = "Reaction type is required")
        private ReactionType reactionType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaItem {
        @NotBlank
        private String url;

        @NotBlank
        private String mediaType;

        private String thumbnailUrl;
        private Integer width;
        private Integer height;
        private Integer durationSeconds;
        private Long fileSize;
        private String altText;
    }
}
