package com.billboard.social.common.client;

import com.billboard.social.common.config.FeignConfig;
import com.billboard.social.common.dto.ApiResponse;
import com.billboard.social.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "sso-service",
        configuration = FeignConfig.class,
        fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{userId}/basic")
    ApiResponse<UserSummary> getUserSummary(@PathVariable Long userId);
}
