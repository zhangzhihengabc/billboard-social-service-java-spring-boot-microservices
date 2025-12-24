package com.ossn.content.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class SocialGraphClientFallback implements SocialGraphClient {

    @Override
    public List<UUID> getFriendIds(UUID userId) {
        log.warn("Fallback: Could not fetch friend IDs for userId: {}", userId);
        return Collections.emptyList();
    }

    @Override
    public boolean areFriends(UUID userId, UUID otherUserId) {
        log.warn("Fallback: Could not check friendship between {} and {}", userId, otherUserId);
        return false;
    }
}
