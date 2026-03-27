package com.billboard.social.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySlotDto {
    private Long userId;
    private String dayOfWeek;
    private String startTime;
    private String endTime;
    private String timezone;
}