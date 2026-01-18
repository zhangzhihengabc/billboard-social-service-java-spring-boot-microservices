package com.billboard.social.graph.controller;

import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.entity.enums.ReactionType;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.service.ReactionService;
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
@RequestMapping("/reactions")
@RequiredArgsConstructor
@Tag(name = "Reactions", description = "Content reactions (likes, loves, etc.)")
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping
    @Operation(summary = "Add or update a reaction")
    public ResponseEntity<ReactionResponse> react(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReactionRequest request) {
        ReactionResponse response = reactionService.react(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{contentType}/{contentId}")
    @Operation(summary = "Remove a reaction")
    public ResponseEntity<Void> removeReaction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable ContentType contentType,
            @PathVariable UUID contentId) {
        reactionService.removeReaction(principal.getId(), contentType, contentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{contentType}/{contentId}")
    @Operation(summary = "Get reactions for content")
    public ResponseEntity<Page<ReactionResponse>> getReactions(
            @PathVariable ContentType contentType,
            @PathVariable UUID contentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ReactionResponse> response = reactionService.getReactions(contentType, contentId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{contentType}/{contentId}/type/{reactionType}")
    @Operation(summary = "Get reactions by type for content")
    public ResponseEntity<Page<ReactionResponse>> getReactionsByType(
            @PathVariable ContentType contentType,
            @PathVariable UUID contentId,
            @PathVariable ReactionType reactionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ReactionResponse> response = reactionService.getReactionsByType(
            contentType, contentId, reactionType, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{contentType}/{contentId}/stats")
    @Operation(summary = "Get reaction statistics for content")
    public ResponseEntity<ReactionStatsResponse> getReactionStats(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable ContentType contentType,
            @PathVariable UUID contentId) {
        UUID userId = principal != null ? principal.getId() : null;
        ReactionStatsResponse response = reactionService.getReactionStats(userId, contentType, contentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{contentType}/{contentId}/check")
    @Operation(summary = "Check if user has reacted")
    public ResponseEntity<Boolean> hasUserReacted(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable ContentType contentType,
            @PathVariable UUID contentId) {
        boolean hasReacted = reactionService.hasUserReacted(principal.getId(), contentType, contentId);
        return ResponseEntity.ok(hasReacted);
    }
}
