package com.billboard.social.group.controller;

import com.billboard.social.group.dto.request.GroupRequests.*;
import com.billboard.social.group.dto.response.GroupResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.group.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
@Tag(name = "Groups", description = "Group management endpoints")
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @Operation(summary = "Create a new group")
    public ResponseEntity<GroupResponse> createGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateGroupRequest request) {
        GroupResponse response = groupService.createGroup(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get group by ID")
    public ResponseEntity<GroupResponse> getGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId) {
        UUID userId = principal != null ? principal.getId() : null;
        GroupResponse response = groupService.getGroup(groupId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get group by slug")
    public ResponseEntity<GroupResponse> getGroupBySlug(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String slug) {
        UUID userId = principal != null ? principal.getId() : null;
        GroupResponse response = groupService.getGroupBySlug(slug, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "Update group")
    public ResponseEntity<GroupResponse> updateGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        GroupResponse response = groupService.updateGroup(principal.getId(), groupId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "Delete group")
    public ResponseEntity<Void> deleteGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId) {
        groupService.deleteGroup(principal.getId(), groupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search groups")
    public ResponseEntity<Page<GroupSummaryResponse>> searchGroups(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<GroupSummaryResponse> response = groupService.searchGroups(query, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/popular")
    @Operation(summary = "Get popular groups")
    public ResponseEntity<Page<GroupSummaryResponse>> getPopularGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<GroupSummaryResponse> response = groupService.getPopularGroups(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/featured")
    @Operation(summary = "Get featured groups")
    public ResponseEntity<Page<GroupSummaryResponse>> getFeaturedGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<GroupSummaryResponse> response = groupService.getFeaturedGroups(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Get groups by category")
    public ResponseEntity<Page<GroupSummaryResponse>> getGroupsByCategory(
            @PathVariable UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<GroupSummaryResponse> response = groupService.getGroupsByCategory(categoryId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    @Operation(summary = "Get current user's groups")
    public ResponseEntity<Page<MembershipResponse>> getMyGroups(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<MembershipResponse> response = groupService.getUserGroups(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }
}
