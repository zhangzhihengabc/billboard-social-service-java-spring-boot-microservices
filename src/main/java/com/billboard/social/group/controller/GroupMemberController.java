package com.billboard.social.group.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.GlobalExceptionHandler.ErrorResponse;
import com.billboard.social.group.dto.request.GroupRequests.*;
import com.billboard.social.group.dto.response.GroupResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.group.service.GroupMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/groups/{groupId}/members")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Group Members", description = "Group membership management")
public class GroupMemberController {

    private final GroupMemberService memberService;

    // ==================== JOIN / LEAVE ====================

    @PostMapping("/join")
    @Operation(summary = "Join a group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Successfully joined group",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or already a member",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> joinGroup(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @RequestBody(required = false) JoinGroupRequest request) {
        GroupMemberResponse response = memberService.joinGroup(principal.getId(), groupId,
                request != null ? request : new JoinGroupRequest());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/leave")
    @Operation(summary = "Leave a group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully left group"),
            @ApiResponse(responseCode = "400", description = "Invalid request or owner cannot leave",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> leaveGroup(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId) {
        memberService.leaveGroup(principal.getId(), groupId);
        return ResponseEntity.noContent().build();
    }

    // ==================== GET MEMBERS ====================

    @GetMapping
    @Operation(summary = "Get group members")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved members"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupMemberResponse>> getMembers(
            @PathVariable UUID groupId,
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<GroupMemberResponse> response = memberService.getMembers(groupId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user's membership in this group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved membership",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Not a member of this group",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> getMyMembership(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId) {
        GroupMemberResponse response = memberService.getMember(groupId, principal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get specific member details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved member",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Member not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> getMember(
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        GroupMemberResponse response = memberService.getMember(groupId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending join requests")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved pending requests"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - moderator access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupMemberResponse>> getPendingMembers(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<GroupMemberResponse> response = memberService.getPendingMembers(principal.getId(), groupId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/banned")
    @Operation(summary = "Get banned members")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved banned members"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - moderator access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupMemberResponse>> getBannedMembers(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<GroupMemberResponse> response = memberService.getBannedMembers(principal.getId(), groupId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admins")
    @Operation(summary = "Get admins and moderators")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved admins",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = GroupMemberResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<GroupMemberResponse>> getAdmins(@PathVariable UUID groupId) {
        List<GroupMemberResponse> response = memberService.getAdminsAndModerators(groupId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ids")
    @Operation(summary = "Get member IDs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved member IDs",
                    content = @Content(array = @ArraySchema(schema = @Schema(type = "string", format = "uuid")))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<UUID>> getMemberIds(@PathVariable UUID groupId) {
        List<UUID> ids = memberService.getMemberIds(groupId);
        return ResponseEntity.ok(ids);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get membership statistics")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved stats",
                    content = @Content(schema = @Schema(implementation = MembershipStatsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MembershipStatsResponse> getMembershipStats(@PathVariable UUID groupId) {
        MembershipStatsResponse response = memberService.getMembershipStats(groupId);
        return ResponseEntity.ok(response);
    }

    // ==================== APPROVE / REJECT ====================

    @PostMapping("/{memberId}/approve")
    @Operation(summary = "Approve join request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully approved request",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or not pending",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - moderator access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> approveJoinRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId) {
        GroupMemberResponse response = memberService.approveJoinRequest(principal.getId(), groupId, memberId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{memberId}/reject")
    @Operation(summary = "Reject join request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully rejected request"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - moderator access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> rejectJoinRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId) {
        memberService.rejectJoinRequest(principal.getId(), groupId, memberId);
        return ResponseEntity.noContent().build();
    }

    // ==================== REMOVE / ROLE ====================

    @DeleteMapping("/{userId}")
    @Operation(summary = "Remove member from group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully removed member"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - cannot remove owner or higher role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> removeMember(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        memberService.removeMember(principal.getId(), groupId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/role")
    @Operation(summary = "Update member role")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated role",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - admin access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> updateMemberRole(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        GroupMemberResponse response = memberService.updateMemberRole(principal.getId(), groupId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/promote-to-admin")
    @Operation(summary = "Promote member to admin (owner only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully promoted to admin",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - only owner can promote to admin",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> promoteToAdmin(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        GroupMemberResponse response = memberService.promoteToAdmin(principal.getId(), groupId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/promote-to-moderator")
    @Operation(summary = "Promote member to moderator (admin+ only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully promoted to moderator",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - admin access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> promoteToModerator(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        GroupMemberResponse response = memberService.promoteToModerator(principal.getId(), groupId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/demote-admin")
    @Operation(summary = "Demote admin to member (owner only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully demoted admin",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - only owner can demote admin",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> demoteAdmin(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        GroupMemberResponse response = memberService.demoteAdmin(principal.getId(), groupId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/demote-moderator")
    @Operation(summary = "Demote moderator to member (admin+ only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully demoted moderator",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - admin access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> demoteModerator(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        GroupMemberResponse response = memberService.demoteModerator(principal.getId(), groupId, userId);
        return ResponseEntity.ok(response);
    }

    // ==================== MUTE / UNMUTE ====================

    @PostMapping("/{userId}/mute")
    @Operation(summary = "Mute a member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully muted member",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - cannot mute admins",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> muteMember(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @Valid @RequestBody MuteMemberRequest request) {
        GroupMemberResponse response = memberService.muteMember(principal.getId(), groupId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/unmute")
    @Operation(summary = "Unmute a member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully unmuted member",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - moderator access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> unmuteMember(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        GroupMemberResponse response = memberService.unmuteMember(principal.getId(), groupId, userId);
        return ResponseEntity.ok(response);
    }

    // ==================== BAN / UNBAN ====================

    @PostMapping("/{userId}/ban")
    @Operation(summary = "Ban a member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully banned member"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - cannot ban owner or higher role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> banMember(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @Valid @RequestBody(required = false) BanMemberRequest request) {
        memberService.banMember(principal.getId(), groupId, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/unban")
    @Operation(summary = "Unban a member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully unbanned member"),
            @ApiResponse(responseCode = "400", description = "Invalid request or member not banned",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - moderator access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> unbanMember(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        memberService.unbanMember(principal.getId(), groupId, userId);
        return ResponseEntity.noContent().build();
    }

    // ==================== SETTINGS ====================

    @PutMapping("/me/settings")
    @Operation(summary = "Update own membership settings")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated settings",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or not a member",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> updateMySettings(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateMemberSettingsRequest request) {
        GroupMemberResponse response = memberService.updateMemberSettings(principal.getId(), groupId, request);
        return ResponseEntity.ok(response);
    }

    // ==================== TRANSFER OWNERSHIP ====================

    @PostMapping("/transfer-ownership/{newOwnerId}")
    @Operation(summary = "Transfer group ownership")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully transferred ownership"),
            @ApiResponse(responseCode = "400", description = "Invalid request or new owner not approved member",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - only owner can transfer",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> transferOwnership(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID newOwnerId) {
        memberService.transferOwnership(principal.getId(), groupId, newOwnerId);
        return ResponseEntity.ok().build();
    }
}