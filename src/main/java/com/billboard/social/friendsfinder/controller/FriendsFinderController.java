package com.billboard.social.friendsfinder.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.friendsfinder.dto.FriendsFinderDtos.FriendFinderResultResponse;
import com.billboard.social.friendsfinder.dto.FriendsFinderDtos.FriendSuggestionResponse;
import com.billboard.social.friendsfinder.dto.FriendsFinderDtos.ScrimHistoryResponse;
import com.billboard.social.friendsfinder.service.FriendsFinderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/friends-finder")
@RequiredArgsConstructor
@Validated
@Tag(name = "Friends Finder", description = "Search and discover potential friends based on gaming criteria")
@SecurityRequirement(name = "bearerAuth")
public class FriendsFinderController {

    private final FriendsFinderService friendsFinderService;

    @GetMapping("/search")
    @Operation(summary = "Search players by gaming criteria",
            description = "Search for players by region and skill level, enriched with friendship status and scrim history")
    public ResponseEntity<PageResponse<FriendFinderResultResponse>> searchPlayers(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Integer minSkillLevel,
            @RequestParam(required = false) Integer maxSkillLevel,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<FriendFinderResultResponse> response = friendsFinderService.searchPlayers(
                principal.getId(), region, minSkillLevel, maxSkillLevel, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Get friend suggestions",
            description = "Get algorithmically generated friend suggestions based on scrim history and mutual connections")
    public ResponseEntity<PageResponse<FriendSuggestionResponse>> getSuggestions(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<FriendSuggestionResponse> response = friendsFinderService.getSuggestions(
                principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/suggestions/{suggestionId}/dismiss")
    @Operation(summary = "Dismiss a friend suggestion",
            description = "Mark a friend suggestion as dismissed so it no longer appears")
    public ResponseEntity<Void> dismissSuggestion(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID suggestionId) {
        friendsFinderService.dismissSuggestion(principal.getId(), suggestionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scrim-history")
    @Operation(summary = "Get scrim match history",
            description = "Get the user's scrimmage match history with opponent details and friendship status")
    public ResponseEntity<PageResponse<ScrimHistoryResponse>> getScrimHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        PageResponse<ScrimHistoryResponse> response = friendsFinderService.getScrimHistory(
                principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }
}
