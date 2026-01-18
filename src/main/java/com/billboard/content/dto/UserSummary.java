package com.billboard.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {
    private UUID id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private Boolean isVerified;
    private Integer level;
}
