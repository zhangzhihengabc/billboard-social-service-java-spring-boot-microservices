package com.ossn.content.client;

import com.ossn.content.dto.UserSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public UserSummary getUserSummary(UUID userId) {
        log.warn("Fallback: Could not fetch user summary for userId: {}", userId);
        return UserSummary.builder()
            .id(userId)
            .username("Unknown")
            .displayName("Unknown User")
            .avatarUrl(null)
            .isVerified(false)
            .level(0)
            .build();
    }
}
