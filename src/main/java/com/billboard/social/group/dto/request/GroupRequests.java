package com.billboard.social.group.dto.request;

import com.billboard.social.group.entity.enums.GroupType;
import com.billboard.social.group.entity.enums.MemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

public class GroupRequests {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateGroupRequest {
        @NotBlank(message = "Group name is required")
        @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
        private String name;

        @Size(max = 5000, message = "Description cannot exceed 5000 characters")
        private String description;

        private GroupType groupType;
        private UUID categoryId;
        private String location;
        private String website;
        private String rules;
        private Boolean allowMemberPosts;
        private Boolean requirePostApproval;
        private Boolean requireJoinApproval;
        private Boolean allowMemberInvites;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateGroupRequest {
        @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
        private String name;

        @Size(max = 5000, message = "Description cannot exceed 5000 characters")
        private String description;

        private GroupType groupType;
        private UUID categoryId;
        private String location;
        private String website;
        private String rules;
        private Boolean allowMemberPosts;
        private Boolean requirePostApproval;
        private Boolean requireJoinApproval;
        private Boolean allowMemberInvites;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinGroupRequest {
        @Size(max = 500, message = "Message cannot exceed 500 characters")
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InviteMemberRequest {
        private UUID userId;
        @Size(max = 500, message = "Message cannot exceed 500 characters")
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateMemberRoleRequest {
        private MemberRole role;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateMemberSettingsRequest {
        private Boolean notificationsEnabled;
        private Boolean showInProfile;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MuteMemberRequest {
        private Integer durationHours;
        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BanMemberRequest {
        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        private String reason;
    }
}
