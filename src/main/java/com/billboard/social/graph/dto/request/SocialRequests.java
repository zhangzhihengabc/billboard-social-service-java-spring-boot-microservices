package com.billboard.social.graph.dto.request;

import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.entity.enums.ReactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

public class SocialRequests {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FollowRequest {
        @NotNull(message = "User ID is required")
        private Long userId;
        private Boolean notificationsEnabled;
        private Boolean isCloseFriend;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateFollowRequest {
        private Boolean notificationsEnabled;
        private Boolean isCloseFriend;
        private Boolean isMuted;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FriendRequest {
        @NotNull(message = "User ID is required")
        private Long userId;
        @Size(max = 500, message = "Message cannot exceed 500 characters")
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionRequest {
        @NotNull(message = "Content type is required")
        private ContentType contentType;
        @NotNull(message = "Content ID is required")
        private UUID contentId;
        @NotNull(message = "Reaction type is required")
        private ReactionType reactionType;
        private Long contentOwnerId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShareRequest {
        @NotNull(message = "Content type is required")
        private ContentType contentType;
        @NotNull(message = "Content ID is required")
        private UUID contentId;
        private Long contentOwnerId;
        private Long targetUserId;
        @Size(max = 1000, message = "Message cannot exceed 1000 characters")
        private String message;
        private Boolean shareToFeed;
        private Boolean shareToStory;
        private Boolean isPrivateShare;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockRequest {
        @NotNull(message = "User ID is required")
        private Long userId;
        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        private String reason;
        private Boolean blockMessages;
        private Boolean blockPosts;
        private Boolean blockComments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PokeRequest {
        @NotNull(message = "User ID is required")
        private Long userId;
    }
}
