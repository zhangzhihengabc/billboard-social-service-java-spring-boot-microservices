package com.billboard.social.gamegroup.dto.response;

import com.billboard.social.group.entity.enums.GroupType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class GameGroupResponses {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameGroupResponse {
        private UUID id;
        private String name;
        private String slug;
        private String description;
        private GroupType groupType;
        private Long ownerId;
        private Integer memberCount;
        private Boolean isVerified;

        // Game profile fields
        private String gameTag;
        private UUID gameId;
        private String region;
        private String platform;
        private String minRank;
        private String maxRank;
        private Integer scrimCount;
        private BigDecimal winRate;
        private Integer averageElo;
        private Boolean requireGameAccount;
        private String discordServerId;
        private String discordChannelId;

        // Current user context
        private Boolean isMember;
        private Boolean isAdmin;

        @Schema(description = "When the group was created", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrimFilterResponse {
        private UUID id;
        private UUID groupId;
        private String gameTag;
        private String region;
        private String format;
        private String mapPool;
        private Integer minTeamSize;
        private Integer maxTeamSize;
        private Integer minElo;
        private Integer maxElo;
        private String availabilitySlots;
        private Boolean isActive;

        @Schema(description = "When the filter was last broadcast", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime lastBroadcastAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameAccountLinkResponse {
        private UUID id;
        private Long userId;
        private String gameTag;
        private String gameAccountId;
        private String gameAccountName;
        private String verificationStatus;

        @Schema(description = "When the account was verified", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime verifiedAt;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupTeamLinkResponse {
        private UUID id;
        private UUID groupId;
        private Long teamId;
        private String teamName;
        private Long linkedBy;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime linkedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogResponse {
        private UUID id;
        private UUID groupId;
        private Long actorUserId;
        private String action;
        private String targetType;
        private String targetId;
        private String details;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameGroupEmbedResponse {
        private UUID id;
        private String name;
        private String slug;
        private GroupType groupType;
        private String gameTag;
        private String region;
        private Integer memberCount;
        private Boolean isVerified;

        @Schema(description = "Icon URL", nullable = true)
        private String iconUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LfsMatchFoundResponse {
        private UUID groupId;
        private UUID matchedGroupId;
        private String gameTag;
        private String region;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime matchedAt;
    }

    // ===== Section 5: Group-Exclusive Scrim Search =====

    /**
     * Represents a group that is actively broadcasting LFS (Looking for Scrim).
     * Combines group identity, esports profile stats, and the active scrim filter criteria.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LfsGroupResponse {
        private UUID groupId;
        private String groupName;
        private String slug;
        private Integer memberCount;

        // From GameGroupProfile
        private String gameTag;
        private String region;
        private String platform;
        private Integer averageElo;
        private Integer scrimCount;

        // From GroupScrimFilter (the active broadcast criteria)
        private String format;
        private String mapPool;
        private Integer minTeamSize;
        private Integer maxTeamSize;
        private Integer minElo;
        private Integer maxElo;
        private String availabilitySlots;

        @Schema(description = "When the LFS was last broadcast")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime lastBroadcastAt;
    }

    // ===== Section 5: Group Leaderboards =====

    /**
     * A single entry in the group leaderboard for a given game tag.
     * Groups are ranked by win rate, scrim count, or average ELO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaderboardEntryResponse {
        private UUID groupId;
        private String groupName;
        private String slug;
        private Integer memberCount;
        private String gameTag;
        private String region;
        private Integer scrimCount;
        private BigDecimal winRate;
        private Integer averageElo;
    }

    // ===== Section 5: Group Chat Channels =====

    /**
     * The chat channel provisioned for a game group by the social chat service.
     * The chatChannelId is populated asynchronously after group creation via the
     * group.chat.channel.created Kafka event.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatChannelResponse {
        @Schema(description = "The game group ID this channel belongs to")
        private UUID groupId;

        @Schema(description = "The chat service channel ID, null if not yet provisioned")
        private String chatChannelId;
    }
}