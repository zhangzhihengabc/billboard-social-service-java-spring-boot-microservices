package com.billboard.social.graph.controller;

import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.service.PokeService;
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
@RequestMapping("/pokes")
@RequiredArgsConstructor
@Tag(name = "Pokes", description = "Poke feature")
public class PokeController {

    private final PokeService pokeService;

    @PostMapping
    @Operation(summary = "Poke a user")
    public ResponseEntity<PokeResponse> poke(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PokeRequest request) {
        PokeResponse response = pokeService.poke(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{pokeId}/poke-back")
    @Operation(summary = "Poke back")
    public ResponseEntity<PokeResponse> pokeBack(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID pokeId) {
        PokeResponse response = pokeService.pokeBack(principal.getId(), pokeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/received")
    @Operation(summary = "Get received pokes")
    public ResponseEntity<Page<PokeResponse>> getReceivedPokes(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PokeResponse> response = pokeService.getReceivedPokes(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sent")
    @Operation(summary = "Get sent pokes")
    public ResponseEntity<Page<PokeResponse>> getSentPokes(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PokeResponse> response = pokeService.getSentPokes(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count")
    @Operation(summary = "Get active pokes count")
    public ResponseEntity<Long> getActivePokesCount(@AuthenticationPrincipal UserPrincipal principal) {
        long count = pokeService.getActivePokesCount(principal.getId());
        return ResponseEntity.ok(count);
    }

    @DeleteMapping("/{pokeId}/dismiss")
    @Operation(summary = "Dismiss a poke")
    public ResponseEntity<Void> dismissPoke(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID pokeId) {
        pokeService.dismissPoke(principal.getId(), pokeId);
        return ResponseEntity.noContent().build();
    }
}
