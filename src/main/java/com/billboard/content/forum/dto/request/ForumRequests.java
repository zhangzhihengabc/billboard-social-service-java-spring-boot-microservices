package com.billboard.content.forum.dto.request;

import com.billboard.content.forum.entity.enums.ForumType;
import com.billboard.content.forum.entity.enums.VoteType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

public class ForumRequests {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateForumRequest {
        @NotBlank(message = "Forum name is required")
        @Size(min = 2, max = 100)
        private String name;

        @Size(max = 500)
        private String description;

        @Builder.Default
        private ForumType forumType = ForumType.GENERAL;

        private UUID parentId;
        private UUID groupId;
        private String icon;
        private String color;
        private Integer displayOrder;
        private Boolean requiresApproval;
        private Integer minLevelToPost;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateForumRequest {
        @Size(min = 2, max = 100)
        private String name;

        @Size(max = 500)
        private String description;

        private String icon;
        private String color;
        private Integer displayOrder;
        private Boolean isLocked;
        private Boolean requiresApproval;
        private Integer minLevelToPost;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTopicRequest {
        @NotNull(message = "Forum ID is required")
        private UUID forumId;

        @NotBlank(message = "Title is required")
        @Size(min = 5, max = 200)
        private String title;

        @NotBlank(message = "Content is required")
        @Size(min = 10, max = 50000)
        private String content;

        @Size(max = 500)
        private String tags;

        private Boolean isPinned;
        private Boolean isAnnouncement;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateTopicRequest {
        @Size(min = 5, max = 200)
        private String title;

        @Size(min = 10, max = 50000)
        private String content;

        @Size(max = 500)
        private String tags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LockTopicRequest {
        @Size(max = 500)
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostRequest {
        @NotNull(message = "Topic ID is required")
        private UUID topicId;

        @NotBlank(message = "Content is required")
        @Size(min = 1, max = 50000)
        private String content;

        private UUID parentId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePostRequest {
        @NotBlank(message = "Content is required")
        @Size(min = 1, max = 50000)
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoteRequest {
        @NotNull(message = "Vote type is required")
        private VoteType voteType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoveTopicRequest {
        @NotNull(message = "Target forum ID is required")
        private UUID targetForumId;
    }
}
