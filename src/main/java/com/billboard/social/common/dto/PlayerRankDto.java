package com.billboard.social.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRankDto {
    private Long userId;
    private String gameTag;
    private String rank;
    private Integer elo;
}