package com.billboard.social.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal projection of the esports-backend PlayerDTO.
 * Used only to resolve an SSO userId (String) to an esports playerId (Long).
 *
 * Endpoint: GET /api/players/user/{userId}
 *
 * The actual response contains many more fields; unknown properties are
 * silently ignored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerDto {

    /** Auto-generated primary key in the esports-backend players table. */
    private Long id;

    /** The SSO userId stored as a String in the esports-backend. */
    private String user;

    private String gamerTag;
}