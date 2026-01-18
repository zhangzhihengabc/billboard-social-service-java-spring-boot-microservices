package com.billboard.content.forum.controller;

import com.billboard.content.forum.dto.request.ForumRequests.*;
import com.billboard.content.forum.dto.response.ForumResponses.*;
import com.billboard.content.security.UserPrincipal;
import com.billboard.content.forum.service.TopicService;
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
@RequestMapping("/topics")
@RequiredArgsConstructor
@Tag(name = "Topics", description = "Topic/Thread management")
public class TopicController {

    private final TopicService topicService;

    @PostMapping
    @Operation(summary = "Create topic")
    public ResponseEntity<TopicResponse> createTopic(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTopicRequest request) {
        TopicResponse response = topicService.createTopic(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{topicId}")
    @Operation(summary = "Get topic by ID")
    public ResponseEntity<TopicResponse> getTopic(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID topicId) {
        UUID userId = principal != null ? principal.getId() : null;
        TopicResponse response = topicService.getTopic(topicId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/forum/{forumId}/slug/{slug}")
    @Operation(summary = "Get topic by slug")
    public ResponseEntity<TopicResponse> getTopicBySlug(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID forumId,
            @PathVariable String slug) {
        UUID userId = principal != null ? principal.getId() : null;
        TopicResponse response = topicService.getTopicBySlug(forumId, slug, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{topicId}")
    @Operation(summary = "Update topic")
    public ResponseEntity<TopicResponse> updateTopic(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID topicId,
            @Valid @RequestBody UpdateTopicRequest request) {
        TopicResponse response = topicService.updateTopic(principal.getId(), topicId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{topicId}")
    @Operation(summary = "Delete topic")
    public ResponseEntity<Void> deleteTopic(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID topicId) {
        topicService.deleteTopic(principal.getId(), topicId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{topicId}/lock")
    @Operation(summary = "Lock topic")
    public ResponseEntity<TopicResponse> lockTopic(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID topicId,
            @Valid @RequestBody LockTopicRequest request) {
        TopicResponse response = topicService.lockTopic(principal.getId(), topicId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{topicId}/lock")
    @Operation(summary = "Unlock topic")
    public ResponseEntity<TopicResponse> unlockTopic(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID topicId) {
        TopicResponse response = topicService.unlockTopic(principal.getId(), topicId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{topicId}/vote")
    @Operation(summary = "Vote on topic")
    public ResponseEntity<Void> voteTopic(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID topicId,
            @Valid @RequestBody VoteRequest request) {
        topicService.voteTopic(principal.getId(), topicId, request.getVoteType());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{topicId}/subscribe")
    @Operation(summary = "Subscribe to topic")
    public ResponseEntity<Void> subscribeTopic(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID topicId) {
        topicService.subscribeTopic(principal.getId(), topicId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{topicId}/subscribe")
    @Operation(summary = "Unsubscribe from topic")
    public ResponseEntity<Void> unsubscribeTopic(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID topicId) {
        topicService.unsubscribeTopic(principal.getId(), topicId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/forum/{forumId}")
    @Operation(summary = "Get forum topics")
    public ResponseEntity<Page<TopicSummaryResponse>> getForumTopics(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID forumId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        UUID userId = principal != null ? principal.getId() : null;
        Page<TopicSummaryResponse> response = topicService.getForumTopics(forumId, userId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recent")
    @Operation(summary = "Get recent topics")
    public ResponseEntity<Page<TopicSummaryResponse>> getRecentTopics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<TopicSummaryResponse> response = topicService.getRecentTopics(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/trending")
    @Operation(summary = "Get trending topics")
    public ResponseEntity<Page<TopicSummaryResponse>> getTrendingTopics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<TopicSummaryResponse> response = topicService.getTrendingTopics(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search topics")
    public ResponseEntity<Page<TopicSummaryResponse>> searchTopics(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<TopicSummaryResponse> response = topicService.searchTopics(q, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user's topics")
    public ResponseEntity<Page<TopicSummaryResponse>> getUserTopics(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<TopicSummaryResponse> response = topicService.getUserTopics(userId, page, size);
        return ResponseEntity.ok(response);
    }
}
