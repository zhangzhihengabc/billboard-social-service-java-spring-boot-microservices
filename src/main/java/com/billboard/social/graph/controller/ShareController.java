package com.billboard.social.graph.controller;

import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shares")
@RequiredArgsConstructor
@Tag(name = "Shares", description = "Content sharing")
public class ShareController {

    private final ShareService shareService;

    @PostMapping
    @Operation(summary = "Share content")
    public ResponseEntity<ShareResponse> share(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ShareRequest request) {
        ShareResponse response = shareService.share(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/content/{contentType}/{contentId}")
    @Operation(summary = "Get shares for content")
    public ResponseEntity<Page<ShareResponse>> getSharesByContent(
            @PathVariable ContentType contentType,
            @PathVariable UUID contentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ShareResponse> response = shareService.getSharesByContent(contentType, contentId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user")
    @Operation(summary = "Get shares by current user")
    public ResponseEntity<Page<ShareResponse>> getSharesByUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ShareResponse> response = shareService.getSharesByUser(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count/{contentType}/{contentId}")
    @Operation(summary = "Get share count for content")
    public ResponseEntity<Long> getShareCount(
            @PathVariable ContentType contentType,
            @PathVariable UUID contentId) {
        long count = shareService.getShareCount(contentType, contentId);
        return ResponseEntity.ok(count);
    }
}
