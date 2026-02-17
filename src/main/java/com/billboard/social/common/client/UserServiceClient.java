package com.billboard.social.common.client;

import com.billboard.social.common.config.FeignConfig;
import com.billboard.social.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "sso-service",
        configuration = FeignConfig.class,
        fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/api/v1/auth/{userId}/summary")
    UserSummary getUserSummary(@PathVariable UUID userId);
}
