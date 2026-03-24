package com.billboard.social.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one entry in the members list returned by
 * GET /api/teams/{teamId}/members.
 *
 * Only the fields needed for membership validation are mapped here.
 * Possible status values in the esports-backend: ACTIVE, INACTIVE, REMOVED, SUSPENDED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamMemberEntryDto {

    /** Esports-backend primary key of the player. */
    private Long playerId;

    /** Membership status — only "ACTIVE" is considered a valid member. */
    private String status;
}