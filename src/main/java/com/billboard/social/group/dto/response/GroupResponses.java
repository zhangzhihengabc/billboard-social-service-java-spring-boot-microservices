package com.billboard.social.group.dto.response;

import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.group.entity.enums.GroupType;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
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
        private String description;
        private GroupType groupType;
        private UUID ownerId;
        private UUID categoryId;
        private String categoryName;
        private String coverImageUrl;
        private String iconUrl;
        private String location;
        private String website;
        private String rules;
        private Integer memberCount;
        private Integer postCount;
        private Boolean isVerified;
        private Boolean isFeatured;
        private Boolean allowMemberPosts;
        private Boolean requirePostApproval;
        private Boolean requireJoinApproval;
        private Boolean allowMemberInvites;
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
        private String displayName;
        private String avatarUrl;
        private MemberRole role;
        private MemberStatus status;
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
        private UUID userId;
        private MemberRole role;
        private MemberStatus status;
        private LocalDateTime joinedAt;
        private Integer postCount;
        private Integer contributionScore;
        private Boolean notificationsEnabled;
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
        private String groupIconUrl;
        private GroupType groupType;
        private MemberRole role;
        private MemberStatus status;
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
        private UUID inviterId;
        private String inviterName;
        private UUID inviteeId;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryResponse {
        private UUID id;
        private String name;
        private String slug;
        private String description;
        private String iconUrl;
        private Integer groupCount;
    }
}
