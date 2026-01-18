package com.billboard.social.event.service;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.exception.ResourceNotFoundException;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.event.dto.request.EventRequests.*;
import com.billboard.social.event.dto.response.EventResponses.*;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class RsvpService {

    private final EventRepository eventRepository;
    private final EventRsvpRepository rsvpRepository;
    private final EventCoHostRepository coHostRepository;
    private final UserServiceClient userServiceClient;
    private final EventEventPublisher eventPublisher;

    @Transactional
    public RsvpResponse rsvp(UUID userId, UUID eventId, RsvpRequest request) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ValidationException("Cannot RSVP to unpublished event");
        }

        // Check capacity for GOING
        if (request.getStatus() == RsvpStatus.GOING && event.isFull()) {
            throw new ValidationException("Event is at full capacity");
        }

        // Check guest limits
        if (request.getGuestCount() > 0) {
            if (!event.getAllowGuests()) {
                throw new ValidationException("This event does not allow guests");
            }
            if (request.getGuestCount() > event.getGuestsPerRsvp()) {
                throw new ValidationException("Maximum " + event.getGuestsPerRsvp() + " guests allowed");
            }
        }

        EventRsvp rsvp = rsvpRepository.findByEventIdAndUserId(eventId, userId)
            .orElse(EventRsvp.builder()
                .event(event)
                .userId(userId)
                .status(RsvpStatus.INVITED)
                .build());

        RsvpStatus oldStatus = rsvp.getStatus();
        rsvp.respond(request.getStatus());
        rsvp.setGuestCount(request.getGuestCount());
        rsvp.setNote(request.getNote());

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
        EventRsvp rsvp = rsvpRepository.findByEventIdAndUserId(eventId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("RSVP not found"));

        Event event = rsvp.getEvent();
        
        // Don't allow host to cancel their own RSVP
        if (event.getHostId().equals(userId)) {
            throw new ValidationException("Host cannot cancel their RSVP");
        }

        RsvpStatus oldStatus = rsvp.getStatus();
        rsvp.respond(RsvpStatus.NOT_GOING);
        rsvpRepository.save(rsvp);

        updateEventCounts(event, oldStatus, RsvpStatus.NOT_GOING);
        eventRepository.save(event);

        log.info("User {} cancelled RSVP for event {}", userId, eventId);
    }

    @Transactional
    public List<RsvpResponse> inviteUsers(UUID hostId, UUID eventId, InviteRequest request) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        checkInviteAccess(hostId, event);

        List<EventRsvp> invites = request.getUserIds().stream()
            .filter(userId -> !rsvpRepository.existsByEventIdAndUserId(eventId, userId))
            .map(userId -> {
                EventRsvp rsvp = EventRsvp.builder()
                    .event(event)
                    .userId(userId)
                    .status(RsvpStatus.INVITED)
                    .invitedBy(hostId)
                    .build();
                return rsvpRepository.save(rsvp);
            })
            .toList();

        event.setInvitedCount(event.getInvitedCount() + invites.size());
        eventRepository.save(event);

        invites.forEach(rsvp -> eventPublisher.publishInviteSent(rsvp));

        log.info("User {} invited {} users to event {}", hostId, invites.size(), eventId);
        return invites.stream().map(this::mapToRsvpResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<RsvpResponse> getAttendees(UUID eventId, RsvpStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "respondedAt"));
        
        Page<EventRsvp> rsvps;
        if (status != null) {
            rsvps = rsvpRepository.findByEventIdAndStatus(eventId, status, pageRequest);
        } else {
            rsvps = rsvpRepository.findByEventId(eventId, pageRequest);
        }
        
        return rsvps.map(this::mapToRsvpResponse);
    }

    @Transactional(readOnly = true)
    public Page<RsvpResponse> getGoingAttendees(UUID eventId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return rsvpRepository.findGoingAttendees(eventId, pageRequest).map(this::mapToRsvpResponse);
    }

    @Transactional(readOnly = true)
    public List<UUID> getGoingUserIds(UUID eventId) {
        return rsvpRepository.findGoingUserIds(eventId);
    }

    @Transactional
    public RsvpResponse checkIn(UUID adminId, UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        checkManageAccess(adminId, event);

        EventRsvp rsvp = rsvpRepository.findByEventIdAndUserId(eventId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("RSVP not found"));

        if (!rsvp.isGoing()) {
            throw new ValidationException("User is not marked as going");
        }

        rsvp.checkIn();
        rsvp = rsvpRepository.save(rsvp);

        eventPublisher.publishCheckIn(rsvp);

        log.info("User {} checked in to event {} by {}", userId, eventId, adminId);
        return mapToRsvpResponse(rsvp);
    }

    private void checkInviteAccess(UUID userId, Event event) {
        if (event.getHostId().equals(userId)) return;
        coHostRepository.findByEventIdAndUserId(event.getId(), userId)
            .filter(EventCoHost::getCanInvite)
            .orElseThrow(() -> new ForbiddenException("No permission to invite"));
    }

    private void checkManageAccess(UUID userId, Event event) {
        if (event.getHostId().equals(userId)) return;
        coHostRepository.findByEventIdAndUserId(event.getId(), userId)
            .filter(EventCoHost::getCanManageRsvps)
            .orElseThrow(() -> new ForbiddenException("No permission to manage RSVPs"));
    }

    private void updateEventCounts(Event event, RsvpStatus oldStatus, RsvpStatus newStatus) {
        if (oldStatus == RsvpStatus.GOING) event.decrementGoingCount();
        if (oldStatus == RsvpStatus.MAYBE) event.decrementMaybeCount();
        if (newStatus == RsvpStatus.GOING) event.incrementGoingCount();
        if (newStatus == RsvpStatus.MAYBE) event.incrementMaybeCount();
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
            .user(userServiceClient.getUserSummary(rsvp.getUserId()))
            .build();
    }
}
