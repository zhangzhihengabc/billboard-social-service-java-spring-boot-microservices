package com.ossn.content.forum.client;

import com.ossn.content.forum.dto.response.ForumResponses.UserSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public UserSummary getUserSummary(UUID userId) {
        log.warn("Fallback: Unable to fetch user summary for userId: {}", userId);
        return UserSummary.builder()
            .id(userId)
            .username("unknown")
            .displayName("Unknown User")
            .build();
    }
}
