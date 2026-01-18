package com.billboard.social.graph.dto.response;

import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.entity.enums.FriendshipStatus;
import com.billboard.social.graph.entity.enums.ReactionType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class SocialResponses {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FollowResponse {
        private UUID id;
        private UUID followerId;
        private UUID followingId;
        private Boolean notificationsEnabled;
        private Boolean isCloseFriend;
        private Boolean isMuted;
        private LocalDateTime createdAt;
        private UserSummary user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FollowStats {
        private UUID userId;
        private Long followersCount;
        private Long followingCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FollowStatsResponse {
        private UUID userId;
        private Long followersCount;
        private Long followingCount;
        private Boolean isFollowing;
        private Boolean isFollowedBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FriendshipResponse {
        private UUID id;
        private UUID requesterId;
        private UUID addresseeId;
        private FriendshipStatus status;
        private String message;
        private Integer mutualFriendsCount;
        private LocalDateTime acceptedAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FriendResponse {
        private UUID friendId;
        private String username;
        private String displayName;
        private String avatarUrl;
        private Boolean isVerified;
        private Integer mutualFriendsCount;
        private LocalDateTime friendsSince;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FriendStats {
        private UUID userId;
        private Long friendsCount;
        private Long pendingRequestsCount;
        private Long sentRequestsCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionResponse {
        private UUID id;
        private UUID userId;
        private ContentType contentType;
        private UUID contentId;
        private ReactionType reactionType;
        private LocalDateTime createdAt;
        private UserSummary user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionSummary {
        private ContentType contentType;
        private UUID contentId;
        private Long totalCount;
        private Map<ReactionType, Long> countByType;
        private ReactionType userReaction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionStatsResponse {
        private ContentType contentType;
        private UUID contentId;
        private Long totalCount;
        private Map<ReactionType, Long> countByType;
        private Boolean userReacted;
        private ReactionType userReactionType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShareResponse {
        private UUID id;
        private UUID userId;
        private ContentType contentType;
        private UUID contentId;
        private UUID targetUserId;
        private String message;
        private Boolean shareToFeed;
        private Boolean shareToStory;
        private Boolean isPrivateShare;
        private LocalDateTime createdAt;
        private UserSummary user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockResponse {
        private UUID id;
        private UUID blockedId;
        private String reason;
        private LocalDateTime createdAt;
        private UserSummary blockedUser;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PokeResponse {
        private UUID id;
        private UUID pokerId;
        private UUID pokedId;
        private Boolean isActive;
        private Integer pokeCount;
        private LocalDateTime pokedBackAt;
        private LocalDateTime createdAt;
        private UserSummary poker;
    }
}
