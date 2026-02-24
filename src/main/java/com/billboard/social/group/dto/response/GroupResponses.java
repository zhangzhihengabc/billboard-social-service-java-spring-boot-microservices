package com.billboard.social.group.dto.response;

import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.group.entity.enums.GroupType;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

public class GroupResponses {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupResponse {
        private UUID id;
        private String name;
        private String slug;

        @Schema(description = "Group description", nullable = true)
        private String description;

        private GroupType groupType;
        private Long ownerId;

        @Schema(description = "Category ID", nullable = true)
        private UUID categoryId;

        @Schema(description = "Category name", nullable = true)
        private String categoryName;

        @Schema(description = "Cover image URL", nullable = true)
        private String coverImageUrl;

        @Schema(description = "Icon URL", nullable = true)
        private String iconUrl;

        @Schema(description = "Location", nullable = true)
        private String location;

        @Schema(description = "Website URL", nullable = true)
        private String website;

        @Schema(description = "Group rules", nullable = true)
        private String rules;

        private Integer memberCount;
        private Integer postCount;
        private Boolean isVerified;
        private Boolean isFeatured;
        private Boolean allowMemberPosts;
        private Boolean requirePostApproval;
        private Boolean requireJoinApproval;
        private Boolean allowMemberInvites;

        @Schema(description = "When the group was created", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;
        // Current user context
        private Boolean isMember;
        private Boolean isAdmin;
        private Boolean isPending;
        private MemberRole userRole;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupSummaryResponse {
        private UUID id;
        private String name;
        private String slug;
        private GroupType groupType;

        @Schema(description = "Icon URL", nullable = true)
        private String iconUrl;

        private Integer memberCount;
        private Boolean isVerified;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberResponse {
        private UUID id;
        private UUID userId;
        private String username;

        @Schema(description = "Display name", nullable = true)
        private String displayName;

        @Schema(description = "Avatar URL", nullable = true)
        private String avatarUrl;

        private MemberRole role;
        private MemberStatus status;

        @Schema(description = "When the member joined", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime joinedAt;

        private Boolean notificationsEnabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupMemberResponse {
        private UUID id;
        private UUID groupId;
        private Long userId;
        private MemberRole role;
        private MemberStatus status;

        @Schema(description = "When the member joined", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime joinedAt;

        private Integer postCount;
        private Integer contributionScore;
        private Boolean notificationsEnabled;

        @Schema(description = "When the member is muted until", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime mutedUntil;

        private UserSummary user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MembershipResponse {
        private UUID groupId;
        private String groupName;
        private String groupSlug;

        @Schema(description = "Group icon URL", nullable = true)
        private String groupIconUrl;

        private GroupType groupType;
        private MemberRole role;
        private MemberStatus status;

        @Schema(description = "When the member joined", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime joinedAt;

        private Boolean notificationsEnabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvitationResponse {
        private UUID id;
        private UUID groupId;
        private String groupName;

        @Schema(description = "Group icon URL", nullable = true)
        private String groupIconUrl;

        private Long inviterId;

        @Schema(description = "Inviter username", nullable = true)
        private String inviterName;

        @Schema(description = "Invitee user ID", nullable = true)
        private Long inviteeId;

        @Schema(description = "Invitee email for external invites", nullable = true)
        private String inviteeEmail;

        @Schema(description = "Invitation message", nullable = true)
        private String message;

        @Schema(description = "Invitation status", example = "PENDING")
        private String status;

        @Schema(description = "Invite code for link sharing", nullable = true)
        private String inviteCode;

        @Schema(description = "When the invitation was created", example = "2026-01-19T11:33:16Z", type = "string", format = "date-time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime createdAt;

        @Schema(description = "When the invitation expires", example = "2026-01-26T11:33:16Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime expiresAt;

        @Schema(description = "When the invitation was accepted", example = "2026-01-20T11:33:16Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime acceptedAt;

        @Schema(description = "When the invitation was declined", example = "2026-01-20T11:33:16Z", type = "string", format = "date-time", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime declinedAt;

        // Inviter user info
        private UserSummary inviter;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryResponse {
        private UUID id;
        private String name;
        private String slug;

        @Schema(description = "Category description", nullable = true)
        private String description;

        @Schema(description = "Icon URL", nullable = true)
        private String iconUrl;

        private Integer groupCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MembershipStatsResponse {
        @Schema(description = "Total approved members", example = "150")
        private long totalMembers;

        @Schema(description = "Pending join requests", example = "5")
        private long pendingRequests;

        @Schema(description = "Number of admins", example = "3")
        private long adminCount;

        @Schema(description = "Number of moderators", example = "5")
        private long moderatorCount;

        @Schema(description = "Number of banned members", example = "2")
        private long bannedCount;
    }
}