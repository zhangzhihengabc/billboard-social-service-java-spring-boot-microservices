package com.billboard.social.event.controller;

import com.billboard.social.event.dto.request.EventRequests.*;
import com.billboard.social.event.dto.response.EventResponses.*;
import com.billboard.social.event.entity.enums.RsvpStatus;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.event.service.RsvpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}/rsvp")
@RequiredArgsConstructor
@Tag(name = "RSVPs", description = "Event RSVP management")
public class RsvpController {

    private final RsvpService rsvpService;

    @PostMapping
    @Operation(summary = "RSVP to event")
    public ResponseEntity<RsvpResponse> rsvp(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID eventId,
            @Valid @RequestBody RsvpRequest request) {
        return ResponseEntity.ok(rsvpService.rsvp(principal.getId(), eventId, request));
    }

    @DeleteMapping
    @Operation(summary = "Cancel RSVP")
    public ResponseEntity<Void> cancelRsvp(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID eventId) {
        rsvpService.cancelRsvp(principal.getId(), eventId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invite")
    @Operation(summary = "Invite users to event")
    public ResponseEntity<List<RsvpResponse>> inviteUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID eventId,
            @Valid @RequestBody InviteRequest request) {
        return ResponseEntity.ok(rsvpService.inviteUsers(principal.getId(), eventId, request));
    }

    @GetMapping("/attendees")
    @Operation(summary = "Get event attendees")
    public ResponseEntity<Page<RsvpResponse>> getAttendees(
            @PathVariable UUID eventId,
            @RequestParam(required = false) RsvpStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(rsvpService.getAttendees(eventId, status, page, size));
    }

    @GetMapping("/going")
    @Operation(summary = "Get going attendees")
    public ResponseEntity<Page<RsvpResponse>> getGoingAttendees(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(rsvpService.getGoingAttendees(eventId, page, size));
    }

    @GetMapping("/going/ids")
    @Operation(summary = "Get going user IDs")
    public ResponseEntity<List<UUID>> getGoingUserIds(@PathVariable UUID eventId) {
        return ResponseEntity.ok(rsvpService.getGoingUserIds(eventId));
    }

    @PostMapping("/{userId}/check-in")
    @Operation(summary = "Check in attendee")
    public ResponseEntity<RsvpResponse> checkIn(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID eventId,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(rsvpService.checkIn(principal.getId(), eventId, userId));
    }
}
