package com.billboard.social.suggestion.dto.response;

import com.billboard.social.common.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * NEW FILE — Suggested Users feature.
 *
 * Response DTO for a single user suggestion.
 * Computed on-the-fly from friends-of-friends or popular-users queries.
 * No database entity behind this — purely a projection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A suggested user for the current user to connect with")
public class SuggestionResponse {

    @Schema(description = "ID of the suggested user", example = "205")
    private Long suggestedUserId;

    @Schema(description = "Number of mutual friends (0 for popularity-based suggestions)", example = "8")
    private Integer mutualFriendCount;

    @Schema(description = "Human-readable reason for the suggestion",
            example = "8 mutual friends")
    private String reason;

    @Schema(description = "How this suggestion was generated",
            example = "FRIEND_OF_FRIEND",
            allowableValues = {"FRIEND_OF_FRIEND", "POPULAR"})
    private String source;

    @Schema(description = "Summary of the suggested user (username, avatar, etc.) fetched from SSO service",
            nullable = true)
    private UserSummary user;
}