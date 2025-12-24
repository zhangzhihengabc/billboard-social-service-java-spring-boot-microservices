package com.ossn.content.feed.client;

import com.ossn.content.feed.dto.response.FeedResponses.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "identity-service", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/users/{userId}/summary")
    UserSummary getUserSummary(@PathVariable("userId") UUID userId);
}
