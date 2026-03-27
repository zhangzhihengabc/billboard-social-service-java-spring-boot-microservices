package com.billboard.social.common.client;

import com.billboard.social.common.dto.PlayerDto;
import com.billboard.social.common.dto.PlayerStatisticsDto;
import com.billboard.social.common.dto.TeamMembersResponseDto;
import com.billboard.social.common.dto.TeamSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@Slf4j
public class EsportsBackendClientFallback implements EsportsBackendClient {

    @Override
    public PlayerDto getPlayerByUserId(String userId) {
        // Return null — callers must guard against null before reading playerId
        log.warn("EsportsBackendClient fallback: getPlayerByUserId for userId={}", userId);
        return null;
    }

    @Override
    public TeamMembersResponseDto getTeamMembers(Long teamId) {
        // Return empty list — membership check will resolve to false (fail-closed)
        log.warn("EsportsBackendClient fallback: getTeamMembers for team={}", teamId);
        return TeamMembersResponseDto.builder()
                .members(Collections.emptyList())
                .count(0)
                .build();
    }

    @Override
    public TeamSummaryDto getTeamSummary(Long teamId) {
        // Return placeholder so team linking can still store the teamId
        log.warn("EsportsBackendClient fallback: getTeamSummary for team={}", teamId);
        return TeamSummaryDto.builder()
                .id(teamId)
                .name("Unknown Team")
                .build();
    }

    @Override
    public PlayerStatisticsDto getPlayerStatistics(Long playerId) {
        // Return null — rank gate will be skipped (soft gate behaviour)
        log.warn("EsportsBackendClient fallback: getPlayerStatistics for player={}", playerId);
        return null;
    }
}