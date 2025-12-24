package com.ossn.content.forum.controller;

import com.ossn.content.forum.dto.request.ForumRequests.*;
import com.ossn.content.forum.dto.response.ForumResponses.*;
import com.ossn.content.forum.service.ForumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/forums")
@RequiredArgsConstructor
@Tag(name = "Forums", description = "Forum management")
public class ForumController {

    private final ForumService forumService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create forum (Admin only)")
    public ResponseEntity<ForumResponse> createForum(@Valid @RequestBody CreateForumRequest request) {
        ForumResponse response = forumService.createForum(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{forumId}")
    @Operation(summary = "Get forum by ID")
    public ResponseEntity<ForumResponse> getForum(@PathVariable UUID forumId) {
        ForumResponse response = forumService.getForum(forumId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get forum by slug")
    public ResponseEntity<ForumResponse> getForumBySlug(@PathVariable String slug) {
        ForumResponse response = forumService.getForumBySlug(slug);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{forumId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update forum (Admin only)")
    public ResponseEntity<ForumResponse> updateForum(
            @PathVariable UUID forumId,
            @Valid @RequestBody UpdateForumRequest request) {
        ForumResponse response = forumService.updateForum(forumId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{forumId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete forum (Admin only)")
    public ResponseEntity<Void> deleteForum(@PathVariable UUID forumId) {
        forumService.deleteForum(forumId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get all forums")
    public ResponseEntity<List<ForumResponse>> getAllForums() {
        List<ForumResponse> response = forumService.getAllForums();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{forumId}/subforums")
    @Operation(summary = "Get sub-forums")
    public ResponseEntity<List<ForumSummaryResponse>> getSubForums(@PathVariable UUID forumId) {
        List<ForumSummaryResponse> response = forumService.getSubForums(forumId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/group/{groupId}")
    @Operation(summary = "Get group forums")
    public ResponseEntity<List<ForumResponse>> getGroupForums(@PathVariable UUID groupId) {
        List<ForumResponse> response = forumService.getGroupForums(groupId);
        return ResponseEntity.ok(response);
    }
}
