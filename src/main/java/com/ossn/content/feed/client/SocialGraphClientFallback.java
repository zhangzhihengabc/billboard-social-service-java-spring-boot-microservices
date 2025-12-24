package com.ossn.content.feed.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class SocialGraphClientFallback implements SocialGraphClient {

    @Override
    public List<UUID> getFriendIds(UUID userId) {
        log.warn("Fallback: Unable to fetch friend IDs for userId: {}", userId);
        return new ArrayList<>();
    }

    @Override
    public boolean areFriends(UUID userId, UUID otherUserId) {
        log.warn("Fallback: Unable to check friendship for users {} and {}", userId, otherUserId);
        return false;
    }
}
