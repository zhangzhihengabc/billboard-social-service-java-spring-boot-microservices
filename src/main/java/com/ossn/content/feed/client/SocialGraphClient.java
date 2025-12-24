package com.ossn.content.feed.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "social-graph-service", fallback = SocialGraphClientFallback.class)
public interface SocialGraphClient {

    @GetMapping("/friendships/{userId}/friend-ids")
    List<UUID> getFriendIds(@PathVariable("userId") UUID userId);

    @GetMapping("/friendships/{userId}/are-friends")
    boolean areFriends(@PathVariable("userId") UUID userId, @RequestParam("otherUserId") UUID otherUserId);
}
