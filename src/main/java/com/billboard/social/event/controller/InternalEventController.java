package com.billboard.social.event.controller;

import com.billboard.social.event.dto.request.EventRequests.BulkRsvpRequest;
import com.billboard.social.event.dto.request.EventRequests.InternalCreateEventRequest;
import com.billboard.social.event.dto.response.EventResponses.BulkRsvpResult;
import com.billboard.social.event.dto.response.EventResponses.EventResponse;
import com.billboard.social.event.dto.response.EventResponses.EventSummaryResponse;
import com.billboard.social.event.entity.enums.EventStatus;
import com.billboard.social.event.entity.enums.EventType;
import com.billboard.social.event.service.InternalEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/events")
@RequiredArgsConstructor
@Validated
@Tag(name = "Internal Events", description = "S2S internal event endpoints — requires X-Internal-Api-Key header")
public class InternalEventController {

    private final InternalEventService internalEventService;

    @PostMapping
    @Operation(summary = "Create an event from a service (S2S)")
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody InternalCreateEventRequest request) {
        EventResponse response = internalEventService.createEventInternal(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{eventId}/rsvp/bulk")
    @Operation(summary = "Bulk RSVP multiple users to an event")
    public ResponseEntity<BulkRsvpResult> bulkRsvp(
            @PathVariable UUID eventId,
            @Valid @RequestBody BulkRsvpRequest request) {
        BulkRsvpResult result = internalEventService.bulkRsvp(eventId, request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{eventId}/rsvp/non-responded")
    @Operation(summary = "Get user IDs invited to the event who have not yet responded")
    public ResponseEntity<List<Long>> getNonRespondedUsers(
            @PathVariable UUID eventId,
            @RequestParam @NotNull(message = "organisationId is required")
            @Positive(message = "organisationId must be positive") Long organisationId) {
        List<Long> userIds = internalEventService.getNonRespondedUserIds(eventId, organisationId);
        return ResponseEntity.ok(userIds);
    }

    @GetMapping("/by-org")
    @Operation(summary = "Get events for an organisation within a date range")
    public ResponseEntity<List<EventSummaryResponse>> getEventsByOrg(
            @RequestParam @NotNull(message = "organisationId is required")
            @Positive(message = "organisationId must be positive") Long organisationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) EventType eventType) {
        List<EventSummaryResponse> response =
                internalEventService.getEventsByOrg(organisationId, start, end, status, eventType);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "Cancel an event from a service (sets status to CANCELLED)")
    public ResponseEntity<EventResponse> cancelEvent(
            @PathVariable UUID eventId,
            @RequestParam @NotNull(message = "organisationId is required")
            @Positive(message = "organisationId must be positive") Long organisationId) {
        EventResponse response = internalEventService.cancelEventInternal(eventId, organisationId);
        return ResponseEntity.ok(response);
    }
}
