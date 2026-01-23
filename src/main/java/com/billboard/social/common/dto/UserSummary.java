package com.billboard.social.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User summary information")
public class UserSummary {

    @Schema(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Username", example = "johndoe")
    private String username;

    @Schema(description = "Display name", example = "John Doe", nullable = true)
    private String displayName;

    @Schema(description = "Avatar URL", example = "https://example.com/avatar.jpg", nullable = true)
    private String avatarUrl;

    @Schema(description = "Whether the user is verified", example = "false")
    private Boolean isVerified;

    @Schema(description = "Email address", example = "myemail@gmail.com")
    private String email;
}