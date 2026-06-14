package com.billboard.social.common.client;

import com.billboard.social.common.dto.ApiResponse;
import com.billboard.social.common.dto.UserSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public ApiResponse<UserSummary> getUserSummary(Long userId) {
        log.warn("Fallback: Unable to fetch user summary for userId: {}", userId);
        ApiResponse<UserSummary> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setData(UserSummary.builder()
            .id(userId)
            .username("unknown")
            .email("unknown@gmail.com")
            .build());
        return response;
    }
}
