package com.ossn.content.feed.controller;

import com.ossn.content.feed.dto.request.FeedRequests.*;
import com.ossn.content.feed.dto.response.FeedResponses.*;
import com.ossn.content.feed.entity.enums.ReactionType;
import com.ossn.content.feed.security.UserPrincipal;
import com.ossn.content.feed.service.ReactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/posts/{postId}/reactions")
@RequiredArgsConstructor
@Tag(name = "Reactions", description = "Post reaction endpoints")
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping
    @Operation(summary = "React to post")
    public ResponseEntity<Void> reactToPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @Valid @RequestBody ReactRequest request) {
        reactionService.reactToPost(principal.getId(), postId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    @Operation(summary = "Remove reaction from post")
    public ResponseEntity<Void> removeReaction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId) {
        reactionService.removeReaction(principal.getId(), postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get post reactions")
    public ResponseEntity<Page<ReactionResponse>> getPostReactions(
            @PathVariable UUID postId,
            @RequestParam(required = false) ReactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ReactionResponse> response = reactionService.getPostReactions(postId, type, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/counts")
    @Operation(summary = "Get reaction counts by type")
    public ResponseEntity<Map<ReactionType, Integer>> getReactionCounts(
            @PathVariable UUID postId) {
        Map<ReactionType, Integer> counts = reactionService.getReactionCounts(postId);
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user's reaction")
    public ResponseEntity<ReactionType> getUserReaction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId) {
        ReactionType reaction = reactionService.getUserReaction(principal.getId(), postId);
        return ResponseEntity.ok(reaction);
    }
}
