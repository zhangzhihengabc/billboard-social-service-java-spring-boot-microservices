package com.billboard.social.suggestion.controller;

import com.billboard.social.common.exception.GlobalExceptionHandler.ErrorResponse;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.suggestion.dto.response.SuggestionResponse;
import com.billboard.social.suggestion.service.UserSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suggestions")
@RequiredArgsConstructor
@Tag(name = "User Suggestions", description = "Suggested users to connect with based on mutual friends and popularity")
@SecurityRequirement(name = "bearerAuth")
public class UserSuggestionController {

    private final UserSuggestionService suggestionService;

    /**
     * GET /api/v1/suggestions
     */
    @GetMapping
    @Operation(summary = "Get suggested users",
            description = "Returns user suggestions based on mutual friends. "
                    + "Falls back to popular users for new accounts with no connections.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Suggestions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<SuggestionResponse>> getSuggestions(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        List<SuggestionResponse> suggestions = suggestionService.getSuggestions(principal.getId());
        return ResponseEntity.ok(suggestions);
    }
}