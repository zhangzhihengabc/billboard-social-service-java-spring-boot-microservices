package com.billboard.social.event.controller;

import com.billboard.social.event.dto.request.EventRequests.*;
import com.billboard.social.event.dto.response.EventResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.event.service.EventService;
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
@RequestMapping("/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Event management endpoints")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @Operation(summary = "Create a new event")
    public ResponseEntity<EventResponse> createEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(principal.getId(), request));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get event by ID")
    public ResponseEntity<EventResponse> getEvent(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID eventId) {
        return ResponseEntity.ok(eventService.getEvent(eventId, principal != null ? principal.getId() : null));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get event by slug")
    public ResponseEntity<EventResponse> getEventBySlug(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String slug) {
        return ResponseEntity.ok(eventService.getEventBySlug(slug, principal != null ? principal.getId() : null));
    }

    @PutMapping("/{eventId}")
    @Operation(summary = "Update event")
    public ResponseEntity<EventResponse> updateEvent(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID eventId,
            @Valid @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(principal.getId(), eventId, request));
    }

    @PostMapping("/{eventId}/publish")
    @Operation(summary = "Publish event")
    public ResponseEntity<EventResponse> publishEvent(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID eventId) {
        return ResponseEntity.ok(eventService.publishEvent(principal.getId(), eventId));
    }

    @PostMapping("/{eventId}/cancel")
    @Operation(summary = "Cancel event")
    public ResponseEntity<EventResponse> cancelEvent(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID eventId,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(eventService.cancelEvent(principal.getId(), eventId, reason));
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "Delete event")
    public ResponseEntity<Void> deleteEvent(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID eventId) {
        eventService.deleteEvent(principal.getId(), eventId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/upcoming")
    public ResponseEntity<Page<EventSummaryResponse>> getUpcomingEvents(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(eventService.getUpcomingEvents(page, size));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<EventSummaryResponse>> searchEvents(
            @RequestParam String query, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(eventService.searchEvents(query, page, size));
    }

    @GetMapping("/popular")
    public ResponseEntity<Page<EventSummaryResponse>> getPopularEvents(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(eventService.getPopularEvents(page, size));
    }

    @GetMapping("/my/upcoming")
    public ResponseEntity<Page<EventSummaryResponse>> getMyUpcomingEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(eventService.getUserUpcomingEvents(principal.getId(), page, size));
    }

    @GetMapping("/my/hosted")
    public ResponseEntity<Page<EventSummaryResponse>> getMyHostedEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(eventService.getHostedEvents(principal.getId(), page, size));
    }
}
