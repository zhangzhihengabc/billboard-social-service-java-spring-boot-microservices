package com.billboard.social.gamegroup.dto.request;

import com.billboard.social.group.entity.enums.GroupType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class GameGroupRequests {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateGameGroupRequest {
        @NotBlank(message = "Group name is required")
        @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
        private String name;

        @Size(max = 5000, message = "Description cannot exceed 5000 characters")
        private String description;

        /**
         * Visibility of the group.
         * PUBLIC  — anyone can see and join directly.
         * CLOSED  — anyone can see but must request to join (requires approval).
         * PRIVATE — only members can see content.
         * SECRET  — hidden from search entirely.
         * Defaults to PUBLIC if not provided.
         */
        private GroupType groupType;

        @NotBlank(message = "Game tag is required")
        @Size(max = 50, message = "Game tag cannot exceed 50 characters")
        private String gameTag;

        private UUID gameId;

        @Size(max = 30, message = "Region cannot exceed 30 characters")
        private String region;

        @Size(max = 20, message = "Platform cannot exceed 20 characters")
        private String platform;

        @Size(max = 30, message = "Min rank cannot exceed 30 characters")
        private String minRank;

        @Size(max = 30, message = "Max rank cannot exceed 30 characters")
        private String maxRank;

        private Boolean requireGameAccount;

        @Size(max = 30, message = "Discord server ID cannot exceed 30 characters")
        private String discordServerId;

        @Size(max = 30, message = "Discord channel ID cannot exceed 30 characters")
        private String discordChannelId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateGameGroupProfileRequest {
        @Size(max = 30, message = "Region cannot exceed 30 characters")
        private String region;

        @Size(max = 20, message = "Platform cannot exceed 20 characters")
        private String platform;

        @Size(max = 30, message = "Min rank cannot exceed 30 characters")
        private String minRank;

        @Size(max = 30, message = "Max rank cannot exceed 30 characters")
        private String maxRank;

        private Boolean requireGameAccount;

        @Size(max = 30, message = "Discord server ID cannot exceed 30 characters")
        private String discordServerId;

        @Size(max = 30, message = "Discord channel ID cannot exceed 30 characters")
        private String discordChannelId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrimFilterRequest {
        @NotBlank(message = "Game tag is required")
        @Size(max = 50, message = "Game tag cannot exceed 50 characters")
        private String gameTag;

        @Size(max = 30, message = "Region cannot exceed 30 characters")
        private String region;

        @Size(max = 10, message = "Format cannot exceed 10 characters")
        private String format;

        private String mapPool;

        private Integer minTeamSize;

        private Integer maxTeamSize;

        private Integer minElo;

        private Integer maxElo;

        private String availabilitySlots;

        private Boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LfsBroadcastRequest {
        @NotNull(message = "Group ID is required")
        private UUID groupId;

        @Size(max = 500, message = "Message cannot exceed 500 characters")
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkGameAccountRequest {
        @NotBlank(message = "Game tag is required")
        @Size(max = 50, message = "Game tag cannot exceed 50 characters")
        private String gameTag;

        @NotBlank(message = "Game account ID is required")
        @Size(max = 100, message = "Game account ID cannot exceed 100 characters")
        private String gameAccountId;

        @Size(max = 100, message = "Game account name cannot exceed 100 characters")
        private String gameAccountName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkTeamRequest {
        @NotNull(message = "Team ID is required")
        private Long teamId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferOwnershipRequest {
        @NotNull(message = "New owner user ID is required")
        private Long newOwnerId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LfsMatchFoundRequest {
        @NotNull(message = "Group ID is required")
        private UUID groupId;

        @NotNull(message = "Matched group ID is required")
        private UUID matchedGroupId;

        @Size(max = 50, message = "Game tag cannot exceed 50 characters")
        private String gameTag;
    }
}