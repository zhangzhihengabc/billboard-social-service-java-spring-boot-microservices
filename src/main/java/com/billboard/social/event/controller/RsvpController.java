package com.billboard.social.event.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.GlobalExceptionHandler.ErrorResponse;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.event.dto.request.EventRequests.*;
import com.billboard.social.event.dto.response.EventResponses;
import com.billboard.social.event.dto.response.EventResponses.*;
import com.billboard.social.event.entity.enums.RsvpStatus;
import com.billboard.social.event.service.RsvpService;
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
import com.billboard.social.event.dto.response.EventResponses.CoHostResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}/rsvp")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Event RSVPs", description = "Event RSVP and Co-Host management")
public class RsvpController {

    private final RsvpService rsvpService;

    // ==================== RSVP ACTIONS ====================

    @PostMapping
    @Operation(summary = "RSVP to event", description = "Submit or update your RSVP status for an event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "RSVP created/updated successfully",
                    content = @Content(schema = @Schema(implementation = RsvpResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request, event not found, or event at capacity",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RsvpResponse> rsvp(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId,
            @Valid @RequestBody RsvpRequest request) {
        RsvpResponse response = rsvpService.rsvp(principal.getId(), eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping
    @Operation(summary = "Cancel RSVP", description = "Cancel your RSVP for an event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "RSVP cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "RSVP not found or host cannot cancel",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> cancelRsvp(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId) {
        rsvpService.cancelRsvp(principal.getId(), eventId);
        return ResponseEntity.noContent().build();
    }

    // ==================== GET ATTENDEES ====================

    @GetMapping("/attendees")
    @Operation(summary = "Get event attendees", description = "Get paginated list of event attendees, optionally filtered by status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved attendees"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<RsvpResponse>> getAttendees(
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId,
            @Parameter(description = "Filter by RSVP status", required = false,
                    schema = @Schema(implementation = RsvpStatus.class))
            @RequestParam(required = false) RsvpStatus status,
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<RsvpResponse> response = rsvpService.getAttendees(eventId, status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/going")
    @Operation(summary = "Get going attendees", description = "Get paginated list of attendees who are going")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved going attendees"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<RsvpResponse>> getGoingAttendees(
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId,
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<RsvpResponse> response = rsvpService.getGoingAttendees(eventId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/checked-in")
    @Operation(summary = "Get checked-in attendees", description = "Get paginated list of attendees who have checked in")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved checked-in attendees"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<RsvpResponse>> getCheckedInAttendees(
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId,
            @Parameter(description = "Page number", example = "0",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "1000", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @Parameter(description = "Page size", example = "20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<RsvpResponse> response = rsvpService.getCheckedInAttendees(eventId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/going/ids")
    @Operation(summary = "Get going user IDs", description = "Get list of user IDs who are going (for bulk operations)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user IDs"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<Long>> getGoingUserIds(
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId) {
        List<Long> userIds = rsvpService.getGoingUserIds(eventId);
        return ResponseEntity.ok(userIds);
    }

    // ==================== CHECK-IN ====================

    @PostMapping("/{userId}/check-in")
    @Operation(summary = "Check in attendee",
            description = "Host or co-host can check in an attendee. Status changes from GOING to CHECKED_IN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Attendee checked in successfully (status: CHECKED_IN)",
                    content = @Content(schema = @Schema(implementation = RsvpResponse.class))),
            @ApiResponse(responseCode = "400", description = "RSVP not found or user not marked as GOING",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - no permission to manage RSVPs",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RsvpResponse> checkIn(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId,
            @Parameter(description = "User ID to check in", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable Long userId) {
        RsvpResponse response = rsvpService.checkIn(principal.getId(), eventId, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}/check-in")
    @Operation(summary = "Undo check-in",
            description = "Host or co-host can undo a check-in. Status changes from CHECKED_IN back to GOING.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Check-in undone successfully (status: GOING)",
                    content = @Content(schema = @Schema(implementation = RsvpResponse.class))),
            @ApiResponse(responseCode = "400", description = "RSVP not found or user not checked in",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - no permission to manage RSVPs",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RsvpResponse> undoCheckIn(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId,
            @Parameter(description = "User ID to undo check-in", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable Long userId) {
        RsvpResponse response = rsvpService.undoCheckIn(principal.getId(), eventId, userId);
        return ResponseEntity.ok(response);
    }

    // ==================== MY RSVP STATUS ====================

    @GetMapping("/my-status")
    @Operation(summary = "Get my RSVP status", description = "Get current user's RSVP status for this event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved RSVP status",
                    content = @Content(schema = @Schema(implementation = RsvpResponse.class))),
            @ApiResponse(responseCode = "400", description = "RSVP not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RsvpResponse> getMyRsvpStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId) {
        RsvpResponse response = rsvpService.getMyRsvpStatus(principal.getId(), eventId);
        return ResponseEntity.ok(response);
    }

    // ==================== CO-HOST MANAGEMENT (Host only) ====================

    @PostMapping("/co-hosts")
    @Operation(summary = "Add co-host", description = "Host can add a user as co-host with specific permissions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Co-host added successfully",
                    content = @Content(schema = @Schema(implementation = CoHostResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or user already co-host",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - only host can manage co-hosts",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CoHostResponse> addCoHost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId,
            @Valid @RequestBody AddCoHostRequest request) {
        CoHostResponse response = rsvpService.addCoHost(principal.getId(), eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/co-hosts/{userId}")
    @Operation(summary = "Remove co-host", description = "Host can remove a co-host from the event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Co-host removed successfully"),
            @ApiResponse(responseCode = "400", description = "Co-host not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - only host can manage co-hosts",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> removeCoHost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId,
            @Parameter(description = "Co-host user ID to remove", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable Long userId) {
        rsvpService.removeCoHost(principal.getId(), eventId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/co-hosts")
    @Operation(summary = "Get all co-hosts", description = "Get list of co-hosts for an event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved co-hosts"),
            @ApiResponse(responseCode = "400", description = "Event not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<EventResponses.CoHostResponse>> getCoHosts(
            @Parameter(description = "Event ID", required = true,
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID eventId) {
        List<EventResponses.CoHostResponse> response = rsvpService.getCoHosts(eventId);
        return ResponseEntity.ok(response);
    }
}