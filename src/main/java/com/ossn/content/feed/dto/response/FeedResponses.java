package com.ossn.content.feed.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ossn.content.dto.UserSummary;
import com.ossn.content.feed.entity.enums.PostType;
import com.ossn.content.feed.entity.enums.PostVisibility;
import com.ossn.content.feed.entity.enums.ReactionType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedResponses {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostResponse {
        private UUID id;
        private UserSummary author;
        private UUID wallOwnerId;
        private UserSummary wallOwner;
        private UUID groupId;
        private String groupName;

        private PostType postType;
        private PostVisibility visibility;

        private String content;

        // Link preview
        private String linkUrl;
        private String linkTitle;
        private String linkDescription;
        private String linkImage;

        // Shared post
        private PostResponse sharedPost;

        // Media
        private List<MediaResponse> mediaItems;
        private List<MentionResponse> mentions;

        // Counts
        private Integer likeCount;
        private Integer loveCount;
        private Integer commentCount;
        private Integer shareCount;
        private Integer viewCount;

        // Reaction breakdown
        private Map<ReactionType, Integer> reactionCounts;

        // Settings
        private Boolean isPinned;
        private Boolean isHighlighted;
        private Boolean allowComments;
        private Boolean allowReactions;

        private String feeling;
        private String location;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // Current user context
        private ReactionType userReaction;
        private Boolean isAuthor;
        private Boolean canEdit;
        private Boolean canDelete;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostSummaryResponse {
        private UUID id;
        private UserSummary author;
        private PostType postType;
        private String contentPreview;
        private String thumbnailUrl;
        private Integer likeCount;
        private Integer commentCount;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaResponse {
        private UUID id;
        private String mediaType;
        private String url;
        private String thumbnailUrl;
        private Integer width;
        private Integer height;
        private Integer durationSeconds;
        private String altText;
        private Integer displayOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentionResponse {
        private UUID userId;
        private String username;
        private String displayName;
        private Integer positionStart;
        private Integer positionEnd;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentResponse {
        private UUID id;
        private UUID postId;
        private UserSummary author;
        private UUID parentId;

        private String content;
        private String mediaUrl;
        private String mediaType;

        private Integer likeCount;
        private Integer replyCount;

        private Boolean isEdited;
        private Boolean isPinned;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // Current user context
        private Boolean hasLiked;
        private Boolean isAuthor;
        private Boolean canEdit;
        private Boolean canDelete;

        // Nested replies (optional, for first few)
        private List<CommentResponse> topReplies;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionResponse {
        private UUID id;
        private UUID postId;
        private UserSummary user;
        private ReactionType reactionType;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedStatsResponse {
        private Long totalPosts;
        private Long totalComments;
        private Long totalReactions;
        private Long postsToday;
        private Long postsThisWeek;
    }
}
