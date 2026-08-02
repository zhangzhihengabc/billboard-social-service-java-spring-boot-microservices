package com.billboard.social.graph.controller;

import com.billboard.social.graph.service.BlockService;
import com.billboard.social.graph.service.FriendshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/graph")
@RequiredArgsConstructor
@Validated
@Tag(name = "Internal Graph", description = "S2S internal graph endpoints — requires X-Internal-Api-Key header")
public class InternalGraphController {

    private final FriendshipService friendshipService;
    private final BlockService blockService;

    @GetMapping("/friends/check")
    @Operation(summary = "Check whether two users are friends (S2S)")
    public ResponseEntity<Boolean> areFriends(
            @RequestParam @NotNull(message = "userId1 is required")
            @Positive(message = "userId1 must be positive") Long userId1,
            @RequestParam @NotNull(message = "userId2 is required")
            @Positive(message = "userId2 must be positive") Long userId2) {
        boolean result = friendshipService.areFriends(userId1, userId2);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/friends/ids")
    @Operation(summary = "Get the friend IDs for a user (S2S)")
    public ResponseEntity<List<Long>> getFriendIds(
            @RequestParam @NotNull(message = "userId is required")
            @Positive(message = "userId must be positive") Long userId) {
        List<Long> friendIds = friendshipService.getFriendIds(userId);
        return ResponseEntity.ok(friendIds);
    }

    @GetMapping("/blocks/check")
    @Operation(summary = "Check whether either user has blocked the other (S2S)")
    public ResponseEntity<Boolean> isBlockedEitherWay(
            @RequestParam @NotNull(message = "userId1 is required")
            @Positive(message = "userId1 must be positive") Long userId1,
            @RequestParam @NotNull(message = "userId2 is required")
            @Positive(message = "userId2 must be positive") Long userId2) {
        boolean result = blockService.isBlockedEitherWay(userId1, userId2);
        return ResponseEntity.ok(result);
    }
}
