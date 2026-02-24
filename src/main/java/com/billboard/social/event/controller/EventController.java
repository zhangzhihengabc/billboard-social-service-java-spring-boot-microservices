package com.billboard.social.event.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.GlobalExceptionHandler.ErrorResponse;
import com.billboard.social.event.dto.request.EventRequests.*;
import com.billboard.social.event.dto.response.EventResponses.*;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Events", description = "Event management endpoints")
public class EventController {

    private final EventService eventService;

    // ==================== CREATE / UPDATE / DELETE ====================

    @PostMapping
    @Operation(summary = "Create a new event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Event created successfully",
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EventResponse> createEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.createEvent(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{eventId}")
    @Operation(summary = "Update event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Event updated successfully",
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or event not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - no edit permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EventResponse> updateEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID eventId,
            @Valid @RequestBody UpdateEventRequest request) {
        EventResponse response = eventService.updateEvent(principal.getId(), eventId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "Delete event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Event deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Event not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - only host can delete",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID eventId) {
        eventService.deleteEvent(principal.getId(), eventId);
        return ResponseEntity.noContent().build();
    }

    // ==================== GET EVENT ====================

    @GetMapping("/{eventId}")
    @Operation(summary = "Get event by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved event",
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "400", description = "Event not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - no view permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EventResponse> getEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID eventId) {
        EventResponse response = eventService.getEvent(eventId, principal != null ? principal.getId() : null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get event by slug")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved event",
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "400", description = "Event not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - no view permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EventResponse> getEventBySlug(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String slug) {
        EventResponse response = eventService.getEventBySlug(slug, principal != null ? principal.getId() : null);
        return ResponseEntity.ok(response);
    }

    // ==================== PUBLISH / CANCEL ====================

    @PostMapping("/{eventId}/publish")
    @Operation(summary = "Publish event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Event published successfully",
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or only draft can be published",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - no edit permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EventResponse> publishEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID eventId) {
        EventResponse response = eventService.publishEvent(principal.getId(), eventId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{eventId}/cancel")
    @Operation(summary = "Cancel event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Event cancelled successfully",
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "400", description = "Event not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - only host can cancel",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EventResponse> cancelEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID eventId,
            @Parameter(description = "Cancellation reason", required = false)
            @RequestParam(required = false) String reason) {
        EventResponse response = eventService.cancelEvent(principal.getId(), eventId, reason);
        return ResponseEntity.ok(response);
    }

    // ==================== LIST EVENTS ====================

    @GetMapping("/upcoming")
    @Operation(summary = "Get upcoming public events")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved events"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<EventSummaryResponse>> getUpcomingEvents(
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<EventSummaryResponse> response = eventService.getUpcomingEvents(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search events")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully searched events"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<EventSummaryResponse>> searchEvents(
            @Parameter(description = "Search query", required = true)
            @RequestParam String query,
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<EventSummaryResponse> response = eventService.searchEvents(query, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/popular")
    @Operation(summary = "Get popular events")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved events"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<EventSummaryResponse>> getPopularEvents(
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<EventSummaryResponse> response = eventService.getPopularEvents(page, size);
        return ResponseEntity.ok(response);
    }

    // ==================== MY EVENTS ====================

    @GetMapping("/my/upcoming")
    @Operation(summary = "Get my upcoming events (events I'm attending)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved events"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<EventSummaryResponse>> getMyUpcomingEvents(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<EventSummaryResponse> response = eventService.getUserUpcomingEvents(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my/hosted")
    @Operation(summary = "Get events I'm hosting or co-hosting")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved events"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<EventSummaryResponse>> getMyHostedEvents(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<EventSummaryResponse> response = eventService.getHostedEvents(principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }
}