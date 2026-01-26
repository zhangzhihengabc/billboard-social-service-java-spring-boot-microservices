package com.billboard.social.event.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.security.InputValidator;
import com.billboard.social.event.dto.request.EventRequests.*;
import com.billboard.social.event.dto.response.EventResponses;
import com.billboard.social.event.dto.response.EventResponses.*;
import com.billboard.social.event.dto.response.EventResponses.CoHostResponse;
import com.billboard.social.event.entity.*;
import com.billboard.social.event.entity.enums.*;
import com.billboard.social.event.event.EventEventPublisher;
import com.billboard.social.event.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RsvpService {

    private final EventRepository eventRepository;
    private final EventRsvpRepository rsvpRepository;
    private final EventCoHostRepository coHostRepository;
    private final UserServiceClient userServiceClient;
    private final EventEventPublisher eventPublisher;

    // ==================== RSVP ACTIONS ====================

    @Transactional
    public RsvpResponse rsvp(UUID userId, UUID eventId, RsvpRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        if (request.getStatus() == null) {
            throw new ValidationException("RSVP status is required");
        }

        // Don't allow setting CHECKED_IN via RSVP endpoint
        if (request.getStatus() == RsvpStatus.CHECKED_IN) {
            throw new ValidationException("Cannot set CHECKED_IN status via RSVP. Use check-in endpoint.");
        }

        Event event = findEventById(eventId);

        // Validate event state
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ValidationException("Cannot RSVP to unpublished event");
        }

        // Check if event is accepting RSVPs
        if (!Boolean.TRUE.equals(event.getAcceptingRsvps())) {
            throw new ValidationException("This event is not accepting RSVPs at this time");
        }

        // Check capacity for GOING
        if (request.getStatus() == RsvpStatus.GOING && event.isFull()) {
            throw new ValidationException("Event is at full capacity");
        }

        // Check guest limits
        int guestCount = request.getGuestCount() != null ? request.getGuestCount() : 0;
        if (guestCount > 0) {
            if (!Boolean.TRUE.equals(event.getAllowGuests())) {
                throw new ValidationException("This event does not allow guests");
            }
            if (guestCount > event.getGuestsPerRsvp()) {
                throw new ValidationException("Maximum " + event.getGuestsPerRsvp() + " guests allowed");
            }
        }

        // Validate note if provided
        String note = null;
        if (request.getNote() != null && !request.getNote().isBlank()) {
            note = InputValidator.validateText(request.getNote(), "Note", 500);
        }

        EventRsvp rsvp = rsvpRepository.findByEventIdAndUserId(eventId, userId)
                .orElse(EventRsvp.builder()
                        .event(event)
                        .userId(userId)
                        .status(RsvpStatus.INVITED)
                        .build());

        // Don't allow changing from CHECKED_IN to other status via RSVP
        if (rsvp.getStatus() == RsvpStatus.CHECKED_IN) {
            throw new ValidationException("Cannot change RSVP status after check-in");
        }

        RsvpStatus oldStatus = rsvp.getStatus();
        rsvp.respond(request.getStatus());
        rsvp.setGuestCount(guestCount);
        rsvp.setNote(note);

        rsvp = rsvpRepository.save(rsvp);

        // Update event counts
        updateEventCounts(event, oldStatus, request.getStatus());
        eventRepository.save(event);

        eventPublisher.publishRsvpChanged(rsvp, oldStatus);

        log.info("User {} RSVP'd {} to event {}", userId, request.getStatus(), eventId);
        return mapToRsvpResponse(rsvp);
    }

    @Transactional
    public void cancelRsvp(UUID userId, UUID eventId) {
        if (eventId == null) {
            throw new ValidationException("Event ID is required");
        }

        EventRsvp rsvp = rsvpRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ValidationException("RSVP not found for this event"));

        Event event = rsvp.getEvent();

        // Don't allow host to cancel their own RSVP
        if (event.getHostId().equals(userId)) {
            throw new ValidationException("Host cannot cancel their RSVP");
        }

        // Don't allow cancelling after check-in
        if (rsvp.getStatus() == RsvpStatus.CHECKED_IN) {
            throw new ValidationException("Cannot cancel RSVP after check-in");
        }

        RsvpStatus oldStatus = rsvp.getStatus();

        // Hard delete the RSVP
        rsvpRepository.delete(rsvp);

        // Update event counts
        if (oldStatus == RsvpStatus.GOING) {
            event.decrementGoingCount();
        } else if (oldStatus == RsvpStatus.MAYBE) {
            event.decrementMaybeCount();
        }
        eventRepository.save(event);

        log.info("User {} cancelled RSVP for event {}", userId, eventId);
    }

    // ==================== GET ATTENDEES ====================

    @Transactional(readOnly = true)
    public PageResponse<RsvpResponse> getAttendees(UUID eventId, RsvpStatus status, int page, int size) {
        if (eventId == null) {
            throw new ValidationException("Event ID is required");
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "respondedAt"));

        Page<EventRsvp> rsvps;
        if (status != null) {
            rsvps = rsvpRepository.findByEventIdAndStatus(eventId, status, pageRequest);
        } else {
            rsvps = rsvpRepository.findByEventIdPageable(eventId, pageRequest);
        }

        return PageResponse.from(rsvps, this::mapToRsvpResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<RsvpResponse> getGoingAttendees(UUID eventId, int page, int size) {
        if (eventId == null) {
            throw new ValidationException("Event ID is required");
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "respondedAt"));
        Page<EventRsvp> rsvps = rsvpRepository.findByEventIdAndStatus(eventId, RsvpStatus.GOING, pageRequest);

        return PageResponse.from(rsvps, this::mapToRsvpResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<RsvpResponse> getCheckedInAttendees(UUID eventId, int page, int size) {
        if (eventId == null) {
            throw new ValidationException("Event ID is required");
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "checkedInAt"));
        Page<EventRsvp> rsvps = rsvpRepository.findByEventIdAndStatus(eventId, RsvpStatus.CHECKED_IN, pageRequest);

        return PageResponse.from(rsvps, this::mapToRsvpResponse);
    }

    @Transactional(readOnly = true)
    public List<UUID> getGoingUserIds(UUID eventId) {
        if (eventId == null) {
            throw new ValidationException("Event ID is required");
        }
        return rsvpRepository.findGoingUserIds(eventId);
    }

    // ==================== CHECK-IN ====================

    @Transactional
    public RsvpResponse checkIn(UUID adminId, UUID eventId, UUID userId) {
        if (eventId == null) {
            throw new ValidationException("Event ID is required");
        }
        if (userId == null) {
            throw new ValidationException("User ID is required");
        }

        Event event = findEventById(eventId);
        checkHostOrCoHost(adminId, event);

        EventRsvp rsvp = rsvpRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ValidationException("RSVP not found for this user"));

        // Already checked in
        if (rsvp.getStatus() == RsvpStatus.CHECKED_IN) {
            throw new ValidationException("User is already checked in");
        }

        // Only GOING users can check in
        if (rsvp.getStatus() != RsvpStatus.GOING) {
            throw new ValidationException("User must be marked as GOING to check in. Current status: " + rsvp.getStatus());
        }

        // Change status to CHECKED_IN and set timestamp
        RsvpStatus oldStatus = rsvp.getStatus();
        rsvp.setStatus(RsvpStatus.CHECKED_IN);
        rsvp.checkIn();
        rsvp = rsvpRepository.save(rsvp);

        // Decrement GOING count since user is now CHECKED_IN
        event.decrementGoingCount();
        eventRepository.save(event);

        eventPublisher.publishCheckIn(rsvp);

        log.info("User {} checked in to event {} by {} (status changed from {} to CHECKED_IN)",
                userId, eventId, adminId, oldStatus);
        return mapToRsvpResponse(rsvp);
    }

    @Transactional
    public RsvpResponse undoCheckIn(UUID adminId, UUID eventId, UUID userId) {
        if (eventId == null) {
            throw new ValidationException("Event ID is required");
        }
        if (userId == null) {
            throw new ValidationException("User ID is required");
        }

        Event event = findEventById(eventId);
        checkHostOrCoHost(adminId, event);

        EventRsvp rsvp = rsvpRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ValidationException("RSVP not found for this user"));

        if (rsvp.getStatus() != RsvpStatus.CHECKED_IN) {
            throw new ValidationException("User is not checked in");
        }

        // Revert to GOING status
        rsvp.setStatus(RsvpStatus.GOING);
        rsvp.setCheckedInAt(null);
        rsvp = rsvpRepository.save(rsvp);

        // Increment GOING count
        event.incrementGoingCount();
        eventRepository.save(event);

        log.info("User {} check-in undone for event {} by {}", userId, eventId, adminId);
        return mapToRsvpResponse(rsvp);
    }

    // ==================== MY STATUS ====================

    @Transactional(readOnly = true)
    public RsvpResponse getMyRsvpStatus(UUID userId, UUID eventId) {
        if (eventId == null) {
            throw new ValidationException("Event ID is required");
        }

        EventRsvp rsvp = rsvpRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ValidationException("You have not RSVP'd to this event"));

        return mapToRsvpResponse(rsvp);
    }

    // ==================== CO-HOST MANAGEMENT ====================

    @Transactional
    public CoHostResponse addCoHost(UUID hostId, UUID eventId, AddCoHostRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        if (request.getUserId() == null) {
            throw new ValidationException("User ID is required");
        }

        Event event = findEventById(eventId);
        checkHostAccess(hostId, event);

        // Cannot add host as co-host
        if (event.getHostId().equals(request.getUserId())) {
            throw new ValidationException("Cannot add event host as co-host");
        }

        // Check if already a co-host
        if (coHostRepository.existsByEventIdAndUserId(eventId, request.getUserId())) {
            throw new ValidationException("User is already a co-host of this event");
        }

        // Verify user exists
        try {
            userServiceClient.getUserSummary(request.getUserId());
        } catch (Exception e) {
            throw new ValidationException("User not found with id: " + request.getUserId());
        }

        // Simple co-host - no permission fields
        EventCoHost coHost = EventCoHost.builder()
                .event(event)
                .userId(request.getUserId())
                .build();

        coHost = coHostRepository.save(coHost);

        log.info("User {} added as co-host to event {} by host {}", request.getUserId(), eventId, hostId);
        return mapToCoHostResponse(coHost);
    }

    @Transactional
    public void removeCoHost(UUID hostId, UUID eventId, UUID userId) {
        Event event = findEventById(eventId);
        checkHostAccess(hostId, event);

        EventCoHost coHost = coHostRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ValidationException("Co-host not found"));

        coHostRepository.delete(coHost);

        log.info("Co-host {} removed from event {} by host {}", userId, eventId, hostId);
    }

    @Transactional(readOnly = true)
    public List<CoHostResponse> getCoHosts(UUID eventId) {
        if (eventId == null) {
            throw new ValidationException("Event ID is required");
        }

        // Verify event exists
        findEventById(eventId);

        return coHostRepository.findByEventId(eventId).stream()
                .map(this::mapToCoHostResponse)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE HELPERS ====================

    private Event findEventById(UUID eventId) {
        if (eventId == null) {
            throw new ValidationException("Event ID is required");
        }
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ValidationException("Event not found with id: " + eventId));
    }

    private void checkHostAccess(UUID userId, Event event) {
        if (!event.getHostId().equals(userId)) {
            throw new ForbiddenException("Only the event host can manage co-hosts");
        }
    }

    /**
     * Check if user is host or co-host (co-hosts can check-in attendees)
     */
    private void checkHostOrCoHost(UUID userId, Event event) {
        // Host has access
        if (event.getHostId().equals(userId)) {
            return;
        }
        // Co-host has access - just check existence, no permission fields
        if (coHostRepository.existsByEventIdAndUserId(event.getId(), userId)) {
            return;
        }
        throw new ForbiddenException("Only host or co-host can perform this action");
    }

    private void updateEventCounts(Event event, RsvpStatus oldStatus, RsvpStatus newStatus) {
        // Decrement old status count
        if (oldStatus == RsvpStatus.GOING) {
            event.decrementGoingCount();
        } else if (oldStatus == RsvpStatus.MAYBE) {
            event.decrementMaybeCount();
        }

        // Increment new status count
        if (newStatus == RsvpStatus.GOING) {
            event.incrementGoingCount();
        } else if (newStatus == RsvpStatus.MAYBE) {
            event.incrementMaybeCount();
        }
    }

    private RsvpResponse mapToRsvpResponse(EventRsvp rsvp) {
        return RsvpResponse.builder()
                .id(rsvp.getId())
                .eventId(rsvp.getEvent().getId())
                .userId(rsvp.getUserId())
                .status(rsvp.getStatus())
                .guestCount(rsvp.getGuestCount())
                .note(rsvp.getNote())
                .respondedAt(rsvp.getRespondedAt())
                .checkedInAt(rsvp.getCheckedInAt())
                .notificationsEnabled(rsvp.getNotificationsEnabled())
                .user(fetchUserSummary(rsvp.getUserId()))
                .build();
    }

    private EventResponses.CoHostResponse mapToCoHostResponse(EventCoHost coHost) {
        return EventResponses.CoHostResponse.builder()
                .id(coHost.getId())
                .eventId(coHost.getEvent().getId())
                .userId(coHost.getUserId())
                .user(fetchUserSummary(coHost.getUserId()))
                .build();
    }

    private UserSummary fetchUserSummary(UUID userId) {
        try {
            return userServiceClient.getUserSummary(userId);
        } catch (Exception e) {
            log.warn("Failed to fetch user summary for {}: {}", userId, e.getMessage());
            return UserSummary.builder()
                    .id(userId)
                    .username("Unknown")
                    .build();
        }
    }
}