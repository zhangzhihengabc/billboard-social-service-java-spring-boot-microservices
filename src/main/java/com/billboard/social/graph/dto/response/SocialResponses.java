package com.billboard.social.graph.dto.response;

import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.entity.enums.FriendshipStatus;
import com.billboard.social.graph.entity.enums.ReactionType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class SocialResponses {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Follow relationship response")
    public static class FollowResponse {
        @Schema(description = "Follow relationship ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;

        @Schema(description = "ID of the follower", example = "44bce359-6715-4e69-87bf-677f332ceb3e")
        private UUID followerId;

        @Schema(description = "ID of the user being followed", example = "c9f95fc2-639d-4b90-ac73-a1de61db8fa7")
        private UUID followingId;

        @Schema(description = "Whether notifications are enabled", example = "true")
        private Boolean notificationsEnabled;

        @Schema(description = "Whether marked as close friend", example = "false")
        private Boolean isCloseFriend;

        @Schema(description = "Whether the user is muted", example = "false")
        private Boolean isMuted;

        @Schema(description = "When the follow was created", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;

        @Schema(description = "Summary of the followed user")
        private UserSummary user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Follow statistics")
    public static class FollowStats {
        @Schema(description = "User ID", example = "44bce359-6715-4e69-87bf-677f332ceb3e")
        private UUID userId;

        @Schema(description = "Number of followers", example = "150")
        private Long followersCount;

        @Schema(description = "Number of following", example = "200")
        private Long followingCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Follow statistics with relationship info")
    public static class FollowStatsResponse {
        @Schema(description = "User ID", example = "44bce359-6715-4e69-87bf-677f332ceb3e")
        private UUID userId;

        @Schema(description = "Number of followers", example = "150")
        private Long followersCount;

        @Schema(description = "Number of following", example = "200")
        private Long followingCount;

        @Schema(description = "Whether current user is following this user", example = "true")
        private Boolean isFollowing;

        @Schema(description = "Whether this user is following current user", example = "false")
        private Boolean isFollowedBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Friendship response")
    public static class FriendshipResponse {
        @Schema(description = "Friendship ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;

        @Schema(description = "ID of the requester", example = "44bce359-6715-4e69-87bf-677f332ceb3e")
        private UUID requesterId;

        @Schema(description = "ID of the addressee", example = "c9f95fc2-639d-4b90-ac73-a1de61db8fa7")
        private UUID addresseeId;

        @Schema(description = "Friendship status", example = "ACCEPTED")
        private FriendshipStatus status;

        @Schema(description = "Request message", example = "Hey, let's be friends!", nullable = true)
        private String message;

        @Schema(description = "Number of mutual friends", example = "5", nullable = true)
        private Integer mutualFriendsCount;

        @Schema(description = "When the friendship was accepted", example = "2026-01-19T12:00:00Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime acceptedAt;

        @Schema(description = "When the request was created", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Friend response")
    public static class FriendResponse {
        @Schema(description = "Friend's user ID", example = "c9f95fc2-639d-4b90-ac73-a1de61db8fa7")
        private UUID friendId;

        @Schema(description = "Friend's username", example = "johndoe", nullable = true)
        private String username;

        @Schema(description = "Number of mutual friends", example = "5", nullable = true)
        private Integer mutualFriendsCount;

        @Schema(description = "When the friendship started", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime friendsSince;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Friend statistics")
    public static class FriendStats {
        @Schema(description = "User ID", example = "44bce359-6715-4e69-87bf-677f332ceb3e")
        private UUID userId;

        @Schema(description = "Number of friends", example = "50")
        private Long friendsCount;

        @Schema(description = "Number of pending requests received", example = "3")
        private Long pendingRequestsCount;

        @Schema(description = "Number of requests sent", example = "2")
        private Long sentRequestsCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Reaction response")
    public static class ReactionResponse {
        @Schema(description = "Reaction ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;

        @Schema(description = "User ID who reacted", example = "44bce359-6715-4e69-87bf-677f332ceb3e")
        private UUID userId;

        @Schema(description = "Type of content reacted to", example = "POST")
        private ContentType contentType;

        @Schema(description = "ID of the content", example = "c9f95fc2-639d-4b90-ac73-a1de61db8fa7")
        private UUID contentId;

        @Schema(description = "Type of reaction", example = "LIKE")
        private ReactionType reactionType;

        @Schema(description = "When the reaction was created", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;

        @Schema(description = "User who reacted", nullable = true)
        private UserSummary user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Reaction summary")
    public static class ReactionSummary {
        @Schema(description = "Type of content", example = "POST")
        private ContentType contentType;

        @Schema(description = "ID of the content", example = "c9f95fc2-639d-4b90-ac73-a1de61db8fa7")
        private UUID contentId;

        @Schema(description = "Total reaction count", example = "42")
        private Long totalCount;

        @Schema(description = "Count by reaction type", nullable = true)
        private Map<ReactionType, Long> countByType;

        @Schema(description = "Current user's reaction type", example = "LIKE", nullable = true)
        private ReactionType userReaction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Reaction statistics response")
    public static class ReactionStatsResponse {
        @Schema(description = "Type of content", example = "POST")
        private ContentType contentType;

        @Schema(description = "ID of the content", example = "c9f95fc2-639d-4b90-ac73-a1de61db8fa7")
        private UUID contentId;

        @Schema(description = "Total reaction count", example = "42")
        private Long totalCount;

        @Schema(description = "Count by reaction type", nullable = true)
        private Map<ReactionType, Long> countByType;

        @Schema(description = "Whether current user has reacted", example = "true")
        private Boolean userReacted;

        @Schema(description = "Current user's reaction type", example = "LIKE", nullable = true)
        private ReactionType userReactionType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Share response")
    public static class ShareResponse {
        @Schema(description = "Share ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;

        @Schema(description = "User ID who shared", example = "44bce359-6715-4e69-87bf-677f332ceb3e")
        private UUID userId;

        @Schema(description = "Type of content shared", example = "POST")
        private ContentType contentType;

        @Schema(description = "ID of the content shared", example = "c9f95fc2-639d-4b90-ac73-a1de61db8fa7")
        private UUID contentId;

        @Schema(description = "ID of the target user (if direct share)", example = "d1f95fc2-639d-4b90-ac73-a1de61db8fa8", nullable = true)
        private UUID targetUserId;

        @Schema(description = "Share message", example = "Check this out!", nullable = true)
        private String message;

        @Schema(description = "Whether shared to feed", example = "true")
        private Boolean shareToFeed;

        @Schema(description = "Whether shared to story", example = "false")
        private Boolean shareToStory;

        @Schema(description = "Whether this is a private share", example = "false")
        private Boolean isPrivateShare;

        @Schema(description = "When the share was created", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;

        @Schema(description = "User who shared", nullable = true)
        private UserSummary user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Block response")
    public static class BlockResponse {
        @Schema(description = "Block ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;

        @Schema(description = "ID of the blocked user", example = "c9f95fc2-639d-4b90-ac73-a1de61db8fa7")
        private UUID blockedId;

        @Schema(description = "Reason for blocking", example = "Spam", nullable = true)
        private String reason;

        @Schema(description = "When the block was created", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;

        @Schema(description = "Blocked user details", nullable = true)
        private UserSummary blockedUser;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Poke response")
    public static class PokeResponse {
        @Schema(description = "Poke ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;

        @Schema(description = "ID of the user who poked", example = "44bce359-6715-4e69-87bf-677f332ceb3e")
        private UUID pokerId;

        @Schema(description = "ID of the user who was poked", example = "c9f95fc2-639d-4b90-ac73-a1de61db8fa7")
        private UUID pokedId;

        @Schema(description = "Whether the poke is active", example = "true")
        private Boolean isActive;

        @Schema(description = "Number of times poked", example = "3")
        private Integer pokeCount;

        @Schema(description = "When the user poked back", example = "2026-01-19T12:00:00Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime pokedBackAt;

        @Schema(description = "When the poke was created", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;

        @Schema(description = "User who poked", nullable = true)
        private UserSummary poker;
    }
}