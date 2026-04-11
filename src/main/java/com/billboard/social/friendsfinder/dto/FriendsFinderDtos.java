package com.billboard.social.friendsfinder.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

public class FriendsFinderDtos {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Player search result with friendship context")
    public static class FriendFinderResultResponse {
        @Schema(description = "SSO user ID", example = "42")
        private Long userId;

        @Schema(description = "Player's gamer tag", example = "ShadowBlade99")
        private String gamerTag;

        @Schema(description = "Skill level 1-10", example = "7")
        private Integer skillLevel;

        @Schema(description = "Player's region/country", example = "SEA")
        private String region;

        @Schema(description = "Avatar URL", nullable = true)
        private String avatarUrl;

        @Schema(description = "Friendship status: NONE, PENDING, or ACCEPTED", example = "NONE")
        private String friendshipStatus;

        @Schema(description = "Number of mutual friends", example = "3")
        private Integer mutualFriendCount;

        @Schema(description = "Number of scrims played together", example = "2")
        private Integer scrimCount;

        @Schema(description = "When they last scrimmaged together", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime lastScrimAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Algorithmic friend suggestion")
    public static class FriendSuggestionResponse {
        @Schema(description = "Suggestion ID")
        private UUID id;

        @Schema(description = "Suggested user's SSO ID", example = "55")
        private Long suggestedUserId;

        @Schema(description = "Suggested user's gamer tag", example = "NightHawk42")
        private String gamerTag;

        @Schema(description = "Avatar URL", nullable = true)
        private String avatarUrl;

        @Schema(description = "Suggestion score 0-100", example = "72.5")
        private Double suggestionScore;

        @Schema(description = "How the suggestion was generated", example = "SCRIM_OPPONENT")
        private String source;

        @Schema(description = "Primary game mode in common", example = "5v5", nullable = true)
        private String gameMode;

        @Schema(description = "Times scrimmaged together", example = "3")
        private Integer interactionCount;

        @Schema(description = "Mutual friend count", example = "2")
        private Integer mutualFriendCount;

        @Schema(description = "When the suggestion was created")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Scrim match history entry")
    public static class ScrimHistoryResponse {
        @Schema(description = "History record ID")
        private UUID id;

        @Schema(description = "Opponent's SSO user ID", example = "55")
        private Long opponentUserId;

        @Schema(description = "Opponent's gamer tag", example = "NightHawk42")
        private String opponentGamerTag;

        @Schema(description = "Game mode played", example = "5v5")
        private String gameMode;

        @Schema(description = "Match quality score 0-100", example = "78.3")
        private Double matchQualityScore;

        @Schema(description = "When the match was played")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime playedAt;

        @Schema(description = "Friendship status with opponent: NONE, PENDING, or ACCEPTED", example = "NONE")
        private String friendshipStatus;
    }
}
