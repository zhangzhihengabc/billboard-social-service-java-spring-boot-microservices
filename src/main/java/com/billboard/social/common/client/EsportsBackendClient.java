package com.billboard.social.common.client;

import com.billboard.social.common.config.FeignConfig;
import com.billboard.social.common.dto.PlayerDto;
import com.billboard.social.common.dto.PlayerStatisticsDto;
import com.billboard.social.common.dto.TeamMembersResponseDto;
import com.billboard.social.common.dto.TeamSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "esports-backend",
        url = "${esports.backend.url}",
        configuration = FeignConfig.class,
        fallback = EsportsBackendClientFallback.class)
public interface EsportsBackendClient {

    /**
     * Req 1 — Step 1: resolve SSO userId (Long from JWT) to esports playerId (Long).
     *
     * The esports-backend stores the SSO reference as a String column (user_id),
     * so userId must be passed as a String path variable.
     *
     * Endpoint: GET /api/players/user/{userId}
     */
    @GetMapping("/api/players/user/{userId}")
    PlayerDto getPlayerByUserId(@PathVariable("userId") String userId);

    /**
     * Req 1 — Step 2: fetch all members of a team.
     *
     * Used together with getPlayerByUserId to check active membership:
     * scan the returned list for playerId == playerDto.getId() && status == "ACTIVE".
     *
     * Endpoint: GET /api/teams/{teamId}/members
     */
    @GetMapping("/api/teams/{teamId}/members")
    TeamMembersResponseDto getTeamMembers(@PathVariable("teamId") Long teamId);

    /**
     * Req 2: get team basic info (id, name) to display on the linked group.
     *
     * The actual response contains more fields; TeamSummaryDto only maps id and name.
     *
     * Endpoint: GET /api/teams/{teamId}
     */
    @GetMapping("/api/teams/{teamId}")
    TeamSummaryDto getTeamSummary(@PathVariable("teamId") Long teamId);

    /**
     * Req 3: get player statistics for rank computation.
     *
     * Returns raw stats (kd_ratio, win_rate, wins, etc.) used to compute a composite
     * MVP score (0-100) in GameGroupService.computeMvpScore().
     * That score is stored in PlayerRankDto.elo for rank-gated join checks.
     *
     * Endpoint: GET /api/statistics/players/{playerId}
     */
    @GetMapping("/api/statistics/players/{playerId}")
    PlayerStatisticsDto getPlayerStatistics(@PathVariable("playerId") Long playerId);

    /**
     * Friends-finder: search players by region/skill (esports-backend).
     * Set {@code esports.backend.url} to include context path if the API is under one (e.g. {@code /egame}).
     */
    @GetMapping("/api/players/search/criteria")
    List<PlayerDto> searchPlayersByCriteria(
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "minSkillLevel", required = false) Integer minSkillLevel,
            @RequestParam(value = "maxSkillLevel", required = false) Integer maxSkillLevel,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);
}