package com.billboard.content.forum.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.billboard.content.dto.UserSummary;
import com.billboard.content.forum.entity.enums.ForumType;
import com.billboard.content.forum.entity.enums.TopicStatus;
import com.billboard.content.forum.entity.enums.VoteType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForumResponses {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForumResponse {
        private UUID id;
        private UUID parentId;
        private UUID groupId;
        private String name;
        private String slug;
        private String description;
        private ForumType forumType;
        private String icon;
        private String color;
        private Integer displayOrder;
        private Integer topicCount;
        private Integer postCount;
        private Boolean isLocked;
        private Boolean requiresApproval;
        private Integer minLevelToPost;
        private LocalDateTime lastPostAt;
        private LocalDateTime createdAt;
        private List<ForumSummaryResponse> subForums;
        private TopicSummaryResponse lastTopic;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForumSummaryResponse {
        private UUID id;
        private String name;
        private String slug;
        private String description;
        private String icon;
        private Integer topicCount;
        private Integer postCount;
        private Boolean isLocked;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicResponse {
        private UUID id;
        private UUID forumId;
        private String forumName;
        private String forumSlug;
        private String title;
        private String slug;
        private String content;
        private TopicStatus status;
        private Boolean isPinned;
        private Boolean isSticky;
        private Boolean isAnnouncement;
        private Boolean isFeatured;
        private Integer replyCount;
        private Integer viewCount;
        private Integer upvoteCount;
        private Integer downvoteCount;
        private Integer score;
        private String tags;
        private LocalDateTime lastPostAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Boolean isEdited;
        private LocalDateTime editedAt;
        private LocalDateTime lockedAt;
        private String lockReason;

        // Author
        private UserSummary author;
        private UserSummary lastPostAuthor;

        // Current user context
        private Boolean isAuthor;
        private Boolean canEdit;
        private Boolean canDelete;
        private Boolean canLock;
        private VoteType userVote;
        private Boolean isSubscribed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicSummaryResponse {
        private UUID id;
        private String title;
        private String slug;
        private TopicStatus status;
        private Boolean isPinned;
        private Boolean isSticky;
        private Integer replyCount;
        private Integer viewCount;
        private Integer score;
        private LocalDateTime lastPostAt;
        private LocalDateTime createdAt;
        private UserSummary author;
        private UserSummary lastPostAuthor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostResponse {
        private UUID id;
        private UUID topicId;
        private UUID parentId;
        private String content;
        private Integer upvoteCount;
        private Integer downvoteCount;
        private Integer score;
        private Integer replyCount;
        private Boolean isSolution;
        private Boolean isEdited;
        private LocalDateTime editedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // Author
        private UserSummary author;

        // Nested replies
        private List<PostResponse> replies;

        // Current user context
        private Boolean isAuthor;
        private Boolean canEdit;
        private Boolean canDelete;
        private Boolean canMarkSolution;
        private VoteType userVote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForumStatsResponse {
        private Integer totalForums;
        private Integer totalTopics;
        private Integer totalPosts;
        private Integer totalUsers;
        private TopicSummaryResponse latestTopic;
        private PostResponse latestPost;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserForumStatsResponse {
        private UUID userId;
        private Integer topicCount;
        private Integer postCount;
        private Integer upvotesReceived;
        private Integer solutionsProvided;
    }
}
