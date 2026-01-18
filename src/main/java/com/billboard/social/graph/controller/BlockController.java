package com.billboard.social.graph.controller;

import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.service.BlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
@Tag(name = "Blocks", description = "User blocking management")
public class BlockController {

    private final BlockService blockService;

    @PostMapping
    @Operation(summary = "Block a user")
    public ResponseEntity<BlockResponse> blockUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BlockRequest request) {
        BlockResponse response = blockService.blockUser(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Unblock a user")
    public ResponseEntity<Void> unblockUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId) {
        blockService.unblockUser(principal.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get blocked users list")
    public ResponseEntity<Page<BlockResponse>> getBlockedUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<BlockResponse> response = blockService.getBlockedUsers(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ids")
    @Operation(summary = "Get blocked user IDs")
    public ResponseEntity<List<UUID>> getBlockedUserIds(@AuthenticationPrincipal UserPrincipal principal) {
        List<UUID> ids = blockService.getBlockedUserIds(principal.getId());
        return ResponseEntity.ok(ids);
    }

    @GetMapping("/check/{userId}")
    @Operation(summary = "Check if a user is blocked")
    public ResponseEntity<Boolean> isBlocked(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId) {
        boolean isBlocked = blockService.isBlocked(principal.getId(), userId);
        return ResponseEntity.ok(isBlocked);
    }
}
