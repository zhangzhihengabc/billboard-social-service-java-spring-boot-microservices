package com.billboard.social.gamegroup.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.gamegroup.dto.request.GameGroupRequests.*;
import com.billboard.social.gamegroup.dto.response.GameGroupResponses.*;
import com.billboard.social.gamegroup.service.AuditService;
import com.billboard.social.gamegroup.service.GameAccountLinkService;
import com.billboard.social.gamegroup.service.GameGroupService;
import com.billboard.social.group.dto.request.GroupRequests.JoinGroupRequest;
import com.billboard.social.group.dto.response.GroupResponses.GroupMemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/game-groups")
@RequiredArgsConstructor
@Validated
@Tag(name = "Game Groups", description = "Esports game group management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class GameGroupController {

    private final GameGroupService gameGroupService;
    private final AuditService auditService;
    private final GameAccountLinkService gameAccountLinkService;

    // 1. Create a game group
    @PostMapping
    @Operation(summary = "Create a new game group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Game group created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<GameGroupResponse> createGameGroup(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateGameGroupRequest request) {
        GameGroupResponse response = gameGroupService.createGameGroup(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. Get a game group by ID
    @GetMapping("/{groupId}")
    @Operation(summary = "Get game group by ID")
    public ResponseEntity<GameGroupResponse> getGameGroup(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(gameGroupService.getGameGroup(groupId, userId));
    }

    // 3. Update game group profile
    @PutMapping("/{groupId}/profile")
    @Operation(summary = "Update game group profile")
    public ResponseEntity<GameGroupResponse> updateGameGroupProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGameGroupProfileRequest request) {
        return ResponseEntity.ok(gameGroupService.updateGameGroupProfile(principal.getId(), groupId, request));
    }

    // 4. Search game groups
    @GetMapping("/search")
    @Operation(summary = "Search game groups by game tag and region")
    public ResponseEntity<PageResponse<GameGroupResponse>> searchGameGroups(
            @RequestParam(required = false, defaultValue = "") String gameTag,
            @RequestParam(required = false, defaultValue = "") String region,
            @RequestParam(defaultValue = "0")  @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100)   int size) {
        return ResponseEntity.ok(gameGroupService.searchGameGroups(gameTag, region, page, size));
    }

    // 5. Public embed endpoint (no auth required)
    @GetMapping("/{groupId}/embed")
    @Operation(summary = "Get group embed data (public)")
    public ResponseEntity<GameGroupEmbedResponse> getGroupEmbed(@PathVariable UUID groupId) {
        return ResponseEntity.ok(gameGroupService.getGroupEmbed(groupId));
    }

    // 6. Save scrim filter
    @PutMapping("/{groupId}/scrim-filter")
    @Operation(summary = "Save scrim filter for a game group")
    public ResponseEntity<ScrimFilterResponse> saveScrimFilter(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody ScrimFilterRequest request) {
        return ResponseEntity.ok(gameGroupService.saveScrimFilter(principal.getId(), groupId, request));
    }

    // 7. Get scrim filter
    @GetMapping("/{groupId}/scrim-filter")
    @Operation(summary = "Get scrim filter for a game group")
    public ResponseEntity<ScrimFilterResponse> getScrimFilter(@PathVariable UUID groupId) {
        return ResponseEntity.ok(gameGroupService.getScrimFilter(groupId));
    }

    // 8. Broadcast LFS
    @PostMapping("/{groupId}/lfs/broadcast")
    @Operation(summary = "Broadcast Looking for Scrim (LFS) to group members")
    public ResponseEntity<Void> broadcastLfs(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody LfsBroadcastRequest request) {
        gameGroupService.broadcastLfs(principal.getId(), groupId, request);
        return ResponseEntity.ok().build();
    }

    // 9. Cancel LFS
    @DeleteMapping("/{groupId}/lfs/broadcast")
    @Operation(summary = "Cancel an active LFS broadcast")
    public ResponseEntity<Void> cancelLfs(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId) {
        gameGroupService.cancelLfs(principal.getId(), groupId);
        return ResponseEntity.noContent().build();
    }

    // 10. Handle LFS match found
    @PostMapping("/{groupId}/lfs/match-found")
    @Operation(summary = "Notify group of an LFS match")
    public ResponseEntity<LfsMatchFoundResponse> handleMatchFound(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody LfsMatchFoundRequest request) {
        return ResponseEntity.ok(gameGroupService.handleMatchFound(principal.getId(), groupId, request));
    }

    // 11. Link a team
    @PostMapping("/{groupId}/teams")
    @Operation(summary = "Link an esports team to the group")
    public ResponseEntity<GroupTeamLinkResponse> linkTeam(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody LinkTeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gameGroupService.linkTeam(principal.getId(), groupId, request));
    }

    // 12. Get linked teams
    @GetMapping("/{groupId}/teams")
    @Operation(summary = "Get all teams linked to a group")
    public ResponseEntity<List<GroupTeamLinkResponse>> getLinkedTeams(@PathVariable UUID groupId) {
        return ResponseEntity.ok(gameGroupService.getLinkedTeams(groupId));
    }

    // 13. Unlink a team
    @DeleteMapping("/{groupId}/teams/{teamId}")
    @Operation(summary = "Unlink a team from the group")
    public ResponseEntity<Void> unlinkTeam(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable Long teamId) {
        gameGroupService.unlinkTeam(principal.getId(), groupId, teamId);
        return ResponseEntity.noContent().build();
    }

    // 14. Transfer group ownership
    @PostMapping("/{groupId}/transfer-ownership")
    @Operation(summary = "Transfer group ownership to another member")
    public ResponseEntity<Void> transferOwnership(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody TransferOwnershipRequest request) {
        gameGroupService.transferOwnership(principal.getId(), groupId, request);
        return ResponseEntity.ok().build();
    }

    // 15. Link game account
    @PostMapping("/accounts/link")
    @Operation(summary = "Link a game account to the requesting user")
    public ResponseEntity<GameAccountLinkResponse> linkGameAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LinkGameAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gameAccountLinkService.linkAccount(principal.getId(), request));
    }

    // 16. Get user game accounts
    @GetMapping("/accounts/me")
    @Operation(summary = "Get all game accounts linked to the current user")
    public ResponseEntity<List<GameAccountLinkResponse>> getMyGameAccounts(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(gameAccountLinkService.getUserAccounts(principal.getId()));
    }

    // 17. Get audit log
    @GetMapping("/{groupId}/audit-log")
    @Operation(summary = "Get admin audit log for a group")
    public ResponseEntity<PageResponse<AuditLogResponse>> getAuditLog(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "0")  @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100)   int size) {
        return ResponseEntity.ok(auditService.getAuditLog(principal.getId(), groupId, page, size));
    }

    // 18. Get member IDs
    @GetMapping("/{groupId}/member-ids")
    @Operation(summary = "Get all approved member user IDs for a group")
    public ResponseEntity<List<Long>> getMemberIds(@PathVariable UUID groupId) {
        return ResponseEntity.ok(gameGroupService.getMemberIds(groupId));
    }

    // 19. Join a game group (rank-gated)
    @PostMapping("/{groupId}/join")
    @Operation(summary = "Join a game group",
            description = "Applies game-specific gate checks (verified account, rank bracket) " +
                    "before creating the membership record.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Join request submitted"),
            @ApiResponse(responseCode = "400", description = "Rank or account requirement not met"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<GroupMemberResponse> joinGameGroup(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody JoinGroupRequest request) {
        return ResponseEntity.ok(gameGroupService.joinGameGroup(principal.getId(), groupId, request));
    }

    // 20. Search LFS groups
    @GetMapping("/lfs/search")
    @Operation(summary = "Search groups that are actively broadcasting LFS (Looking For Scrim)",
            description = "Returns paginated results sorted by most-recent broadcast. " +
                    "ELO range filtering uses overlap logic: groups whose filter ELO " +
                    "window intersects with the caller's (minElo, maxElo) are returned. " +
                    "This endpoint is public so teams can browse without authenticating.")
    public ResponseEntity<PageResponse<LfsGroupResponse>> searchLfsGroups(
            @RequestParam(required = false, defaultValue = "") String gameTag,
            @RequestParam(required = false, defaultValue = "") String region,
            @RequestParam(required = false, defaultValue = "") String format,
            @RequestParam(required = false) Integer minElo,
            @RequestParam(required = false) Integer maxElo,
            @RequestParam(defaultValue = "0")  @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100)   int size) {
        return ResponseEntity.ok(
                gameGroupService.searchLfsGroups(gameTag, region, format, minElo, maxElo, page, size));
    }

    // 21. Group leaderboard
    @GetMapping("/leaderboard")
    @Operation(summary = "Get the group leaderboard for a game tag",
            description = "Ranks groups by win rate, scrim count, or average ELO. " +
                    "sortBy accepts: WIN_RATE (default), SCRIM_COUNT, AVERAGE_ELO. " +
                    "This endpoint is public.")
    public ResponseEntity<PageResponse<LeaderboardEntryResponse>> getLeaderboard(
            @RequestParam(required = false, defaultValue = "") String gameTag,
            @RequestParam(defaultValue = "WIN_RATE") String sortBy,
            @RequestParam(defaultValue = "0")  @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100)   int size) {
        return ResponseEntity.ok(gameGroupService.getLeaderboard(gameTag, sortBy, page, size));
    }

    // 22. Get chat channel for a group
    @GetMapping("/{groupId}/chat-channel")
    @Operation(summary = "Get the chat channel provisioned for a game group",
            description = "The chat channel is created asynchronously when the group is formed. " +
                    "Returns 404 if the channel has not yet been provisioned by the chat service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Chat channel found"),
            @ApiResponse(responseCode = "404", description = "Channel not yet provisioned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ChatChannelResponse> getChatChannel(@PathVariable UUID groupId) {
        return ResponseEntity.ok(gameGroupService.getChatChannel(groupId));
    }
}