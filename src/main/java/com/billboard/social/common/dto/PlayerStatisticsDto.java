package com.billboard.social.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal projection of the esports-backend PlayerDetailedStatisticsDTO.
 * Only the fields required for computing the composite MVP score are mapped.
 *
 * Endpoint: GET /api/statistics/players/{playerId}
 *
 * The composite score (0-100) is computed in GameGroupService.computeMvpScore()
 * and stored in PlayerRankDto.elo for rank-gated join checks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerStatisticsDto {

    private Long playerId;

    /** Total matches played across all tournaments. */
    private Integer matchesPlayed;

    /** Total wins. */
    private Integer wins;

    /** Win rate pre-calculated by esports-backend (wins / matchesPlayed * 100). */
    private Double winRate;

    /** Kill/death ratio. */
    private Double killDeathRatio;

    /** Average score per match. */
    private Double averageScore;

    /** Total tournaments won. */
    private Integer tournamentsWon;
}