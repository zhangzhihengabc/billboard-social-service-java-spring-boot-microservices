package com.billboard.social.group.controller;

import com.billboard.social.group.dto.request.GroupRequests.*;
import com.billboard.social.group.dto.response.GroupResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.group.service.GroupMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/groups/{groupId}/members")
@RequiredArgsConstructor
@Tag(name = "Group Members", description = "Group membership management")
public class GroupMemberController {

    private final GroupMemberService memberService;

    @PostMapping("/join")
    @Operation(summary = "Join a group")
    public ResponseEntity<GroupMemberResponse> joinGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @RequestBody(required = false) JoinGroupRequest request) {
        GroupMemberResponse response = memberService.joinGroup(principal.getId(), groupId, 
            request != null ? request : new JoinGroupRequest());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/leave")
    @Operation(summary = "Leave a group")
    public ResponseEntity<Void> leaveGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId) {
        memberService.leaveGroup(principal.getId(), groupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get group members")
    public ResponseEntity<Page<GroupMemberResponse>> getMembers(
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<GroupMemberResponse> response = memberService.getMembers(groupId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending join requests")
    public ResponseEntity<Page<GroupMemberResponse>> getPendingMembers(
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<GroupMemberResponse> response = memberService.getPendingMembers(groupId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ids")
    @Operation(summary = "Get member IDs")
    public ResponseEntity<List<UUID>> getMemberIds(@PathVariable UUID groupId) {
        List<UUID> ids = memberService.getMemberIds(groupId);
        return ResponseEntity.ok(ids);
    }

    @PostMapping("/{memberId}/approve")
    @Operation(summary = "Approve join request")
    public ResponseEntity<GroupMemberResponse> approveJoinRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId) {
        GroupMemberResponse response = memberService.approveJoinRequest(principal.getId(), groupId, memberId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{memberId}/reject")
    @Operation(summary = "Reject join request")
    public ResponseEntity<Void> rejectJoinRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId) {
        memberService.rejectJoinRequest(principal.getId(), groupId, memberId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Remove member from group")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        memberService.removeMember(principal.getId(), groupId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/role")
    @Operation(summary = "Update member role")
    public ResponseEntity<GroupMemberResponse> updateMemberRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        GroupMemberResponse response = memberService.updateMemberRole(principal.getId(), groupId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/mute")
    @Operation(summary = "Mute a member")
    public ResponseEntity<GroupMemberResponse> muteMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @Valid @RequestBody MuteMemberRequest request) {
        GroupMemberResponse response = memberService.muteMember(principal.getId(), groupId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/ban")
    @Operation(summary = "Ban a member")
    public ResponseEntity<Void> banMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @Valid @RequestBody BanMemberRequest request) {
        memberService.banMember(principal.getId(), groupId, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/transfer-ownership/{newOwnerId}")
    @Operation(summary = "Transfer group ownership")
    public ResponseEntity<Void> transferOwnership(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID newOwnerId) {
        memberService.transferOwnership(principal.getId(), groupId, newOwnerId);
        return ResponseEntity.ok().build();
    }
}
