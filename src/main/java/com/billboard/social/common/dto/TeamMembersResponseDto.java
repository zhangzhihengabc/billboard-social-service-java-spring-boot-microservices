package com.billboard.social.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Wrapper response from GET /api/teams/{teamId}/members.
 *
 * Actual response shape from the esports-backend:
 * {
 *   "members": [ { "playerId": 7, "status": "ACTIVE", ... }, ... ],
 *   "count": 3
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamMembersResponseDto {

    private List<TeamMemberEntryDto> members;
    private Integer count;
}