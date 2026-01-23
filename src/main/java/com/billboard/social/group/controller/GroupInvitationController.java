package com.billboard.social.group.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.GlobalExceptionHandler.ErrorResponse;
import com.billboard.social.group.dto.request.GroupRequests.*;
import com.billboard.social.group.dto.response.GroupResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.group.service.GroupInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Group Invitations", description = "Group invitation management")
public class GroupInvitationController {

    private final GroupInvitationService invitationService;

    // ==================== ADMIN: SEND INVITATIONS ====================

    @PostMapping("/groups/{groupId}/invitations")
    @Operation(summary = "Invite a user to join the group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Invitation sent successfully",
                    content = @Content(schema = @Schema(implementation = InvitationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or user already member",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InvitationResponse> inviteMember(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody InviteMemberRequest request) {
        InvitationResponse response = invitationService.inviteMember(principal.getId(), groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/groups/{groupId}/invitations/link")
    @Operation(summary = "Create a shareable invite link")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Invite link created successfully",
                    content = @Content(schema = @Schema(implementation = InvitationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InvitationResponse> createInviteLink(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody(required = false) CreateInviteLinkRequest request) {
        InvitationResponse response = invitationService.createInviteLink(principal.getId(), groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ==================== ADMIN: GET GROUP INVITATIONS ====================

    @GetMapping("/groups/{groupId}/invitations")
    @Operation(summary = "Get pending invitations for the group (moderator only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved invitations"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - moderator access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<InvitationResponse>> getGroupInvitations(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<InvitationResponse> response = invitationService.getGroupInvitations(
                principal.getId(), groupId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/groups/{groupId}/invitations/{invitationId}")
    @Operation(summary = "Get invitation details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved invitation",
                    content = @Content(schema = @Schema(implementation = InvitationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invitation not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InvitationResponse> getInvitation(
            @PathVariable UUID groupId,
            @PathVariable UUID invitationId) {
        InvitationResponse response = invitationService.getInvitation(invitationId);
        return ResponseEntity.ok(response);
    }

    // ==================== ADMIN: CANCEL INVITATION ====================

    @DeleteMapping("/groups/{groupId}/invitations/{invitationId}")
    @Operation(summary = "Cancel an invitation (moderator only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Invitation cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Invitation not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - moderator access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> cancelInvitation(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID invitationId) {
        invitationService.cancelInvitation(principal.getId(), groupId, invitationId);
        return ResponseEntity.noContent().build();
    }

    // ==================== USER: GET MY INVITATIONS ====================

    @GetMapping("/invitations")
    @Operation(summary = "Get my pending group invitations")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved invitations"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<InvitationResponse>> getMyInvitations(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<InvitationResponse> response = invitationService.getMyPendingInvitations(
                principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/invitations/count")
    @Operation(summary = "Get count of my pending invitations")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved count",
                    content = @Content(schema = @Schema(type = "integer", format = "int64", example = "5"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Long> getInvitationCount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        long count = invitationService.countPendingInvitations(principal.getId());
        return ResponseEntity.ok(count);
    }

    // ==================== USER: ACCEPT / DECLINE ====================

    @PostMapping("/invitations/{invitationId}/accept")
    @Operation(summary = "Accept an invitation and join the group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully joined group",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or invitation expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - invitation not for this user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> acceptInvitation(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID invitationId) {
        GroupMemberResponse response = invitationService.acceptInvitation(principal.getId(), invitationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/invitations/{invitationId}/decline")
    @Operation(summary = "Decline an invitation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Invitation declined successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or invitation expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - invitation not for this user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> declineInvitation(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID invitationId) {
        invitationService.declineInvitation(principal.getId(), invitationId);
        return ResponseEntity.noContent().build();
    }

    // ==================== USER: JOIN BY CODE ====================

    @PostMapping("/invitations/join")
    @Operation(summary = "Join a group using an invite code")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully joined group",
                    content = @Content(schema = @Schema(implementation = GroupMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid code or already a member",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupMemberResponse> joinByCode(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Invite code", required = true, example = "abc123xyz")
            @RequestParam @NotBlank String code) {
        GroupMemberResponse response = invitationService.acceptByCode(principal.getId(), code);
        return ResponseEntity.ok(response);
    }

    // ==================== USER: PREVIEW INVITE ====================

    @GetMapping("/invitations/preview")
    @Operation(summary = "Preview group info from invite code (before joining)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved invitation preview",
                    content = @Content(schema = @Schema(implementation = InvitationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or expired code",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InvitationResponse> previewInvitation(
            @Parameter(description = "Invite code", required = true, example = "abc123xyz")
            @RequestParam @NotBlank String code) {
        InvitationResponse response = invitationService.getInvitationByCode(code);
        return ResponseEntity.ok(response);
    }
}