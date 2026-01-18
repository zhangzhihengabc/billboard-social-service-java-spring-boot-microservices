package com.billboard.social.event.service;
import com.billboard.social.common.dto.UserSummary;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.event.dto.request.EventRequests.*;
import com.billboard.social.event.dto.response.EventResponses.*;
import com.billboard.social.event.entity.Event;
import com.billboard.social.event.entity.EventAttendee;
import com.billboard.social.event.entity.enums.RsvpStatus;
import com.billboard.social.event.event.EventEventPublisher;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.event.repository.EventAttendeeRepository;
import com.billboard.social.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
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
public class EventAttendeeService {

    private final EventRepository eventRepository;
    private final EventAttendeeRepository attendeeRepository;
    private final UserServiceClient userServiceClient;
    private final EventEventPublisher eventPublisher;

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public AttendeeResponse rsvp(UUID userId, UUID eventId, RsvpRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        if (event.isPast()) {
            throw new ValidationException("Cannot RSVP to past events");
        }

        if (request.getStatus() == RsvpStatus.GOING && !event.hasCapacity()) {
            if (event.getRequireApproval()) {
                request.setStatus(RsvpStatus.WAITLIST);
            } else {
                throw new ValidationException("Event has reached maximum capacity");
            }
        }

        if (request.getGuestCount() > 0 && !event.getAllowGuests()) {
            throw new ValidationException("This event does not allow additional guests");
        }

        EventAttendee attendee = attendeeRepository.findByEventIdAndUserId(eventId, userId)
                .orElse(EventAttendee.builder()
                        .event(event)
                        .userId(userId)
                        .build());

        RsvpStatus previousStatus = attendee.getRsvpStatus();
        attendee.rsvp(request.getStatus());
        attendee.setGuestCount(request.getGuestCount());
        attendee.setNote(request.getNote());

        attendee = attendeeRepository.save(attendee);

        // Update event counts
        updateEventCounts(event, previousStatus, request.getStatus());
        eventRepository.save(event);

        eventPublisher.publishRsvpChanged(attendee, previousStatus);

        log.info("User {} RSVP'd {} to event {}", userId, request.getStatus(), eventId);
        return mapToAttendeeResponse(attendee);
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public void cancelRsvp(UUID userId, UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        EventAttendee attendee = attendeeRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("RSVP not found"));

        if (attendee.getIsHost()) {
            throw new ValidationException("Host cannot cancel RSVP");
        }

        RsvpStatus previousStatus = attendee.getRsvpStatus();
        attendee.softDelete();
        attendeeRepository.save(attendee);

        // Update counts
        if (previousStatus == RsvpStatus.GOING) {
            event.decrementGoingCount();
        } else if (previousStatus == RsvpStatus.MAYBE) {
            event.decrementMaybeCount();
        }
        eventRepository.save(event);

        log.info("User {} cancelled RSVP to event {}", userId, eventId);
    }

    @Transactional
    public AttendeeResponse inviteUser(UUID inviterId, UUID eventId, InviteRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        checkHostAccess(inviterId, event);

        if (request.getUserId() == null && request.getEmail() == null) {
            throw new ValidationException("Either userId or email is required");
        }

        UUID inviteeId = request.getUserId();
        if (inviteeId != null && attendeeRepository.existsByEventIdAndUserId(eventId, inviteeId)) {
            throw new ValidationException("User is already invited or attending");
        }

        EventAttendee attendee = EventAttendee.builder()
                .event(event)
                .userId(inviteeId)
                .rsvpStatus(RsvpStatus.INVITED)
                .invitedBy(inviterId)
                .build();

        attendee = attendeeRepository.save(attendee);

        event.setInvitedCount(event.getInvitedCount() + 1);
        eventRepository.save(event);

        eventPublisher.publishUserInvited(attendee, request.getMessage());

        log.info("User {} invited user {} to event {}", inviterId, inviteeId, eventId);
        return mapToAttendeeResponse(attendee);
    }

    @Transactional
    public AttendeeResponse checkIn(UUID hostId, UUID eventId, UUID attendeeId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        checkHostAccess(hostId, event);

        EventAttendee attendee = attendeeRepository.findById(attendeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendee", "id", attendeeId));

        if (!attendee.getEvent().getId().equals(eventId)) {
            throw new ValidationException("Attendee does not belong to this event");
        }

        if (!attendee.isGoing()) {
            throw new ValidationException("Only going attendees can be checked in");
        }

        attendee.checkIn();
        attendee = attendeeRepository.save(attendee);

        eventPublisher.publishAttendeeCheckedIn(attendee);

        log.info("Attendee {} checked in to event {}", attendeeId, eventId);
        return mapToAttendeeResponse(attendee);
    }

    @Transactional
    public AttendeeResponse promoteToCoHost(UUID hostId, UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        if (!event.getHostId().equals(hostId)) {
            throw new ForbiddenException("Only the organizer can promote co-hosts");
        }

        EventAttendee attendee = attendeeRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendee not found"));

        attendee.setIsCoHost(true);
        attendee = attendeeRepository.save(attendee);

        log.info("User {} promoted to co-host of event {} by {}", userId, eventId, hostId);
        return mapToAttendeeResponse(attendee);
    }

    @Transactional
    public void removeAttendee(UUID hostId, UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        checkHostAccess(hostId, event);

        EventAttendee attendee = attendeeRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendee not found"));

        if (attendee.getIsHost()) {
            throw new ForbiddenException("Cannot remove the host");
        }

        RsvpStatus previousStatus = attendee.getRsvpStatus();
        attendee.softDelete();
        attendeeRepository.save(attendee);

        if (previousStatus == RsvpStatus.GOING) {
            event.decrementGoingCount();
        } else if (previousStatus == RsvpStatus.MAYBE) {
            event.decrementMaybeCount();
        }
        eventRepository.save(event);

        log.info("User {} removed from event {} by {}", userId, eventId, hostId);
    }

    @Transactional(readOnly = true)
    public Page<AttendeeResponse> getAttendees(UUID eventId, RsvpStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "rsvpAt"));

        Page<EventAttendee> attendees;
        if (status != null) {
            attendees = attendeeRepository.findByEventIdAndRsvpStatus(eventId, status, pageRequest);
        } else {
            attendees = attendeeRepository.findConfirmedAttendees(eventId, pageRequest);
        }

        return attendees.map(this::mapToAttendeeResponse);
    }

    @Transactional(readOnly = true)
    public List<UUID> getGoingUserIds(UUID eventId) {
        return attendeeRepository.findGoingUserIds(eventId);
    }

    @Transactional(readOnly = true)
    public EventStatsResponse getEventStats(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        long goingCount = attendeeRepository.countGoingAttendees(eventId);
        long maybeCount = attendeeRepository.countMaybeAttendees(eventId);

        return EventStatsResponse.builder()
                .eventId(eventId)
                .goingCount(goingCount)
                .maybeCount(maybeCount)
                .invitedCount((long) event.getInvitedCount())
                .build();
    }

    private void checkHostAccess(UUID userId, Event event) {
        if (event.getHostId().equals(userId)) {
            return;
        }

        EventAttendee attendee = attendeeRepository.findByEventIdAndUserId(event.getId(), userId)
                .orElseThrow(() -> new ForbiddenException("You don't have permission"));

        if (!attendee.getIsHost() && !attendee.getIsCoHost()) {
            throw new ForbiddenException("You don't have permission");
        }
    }

    private void updateEventCounts(Event event, RsvpStatus previousStatus, RsvpStatus newStatus) {
        // Decrement old status
        if (previousStatus == RsvpStatus.GOING) {
            event.decrementGoingCount();
        } else if (previousStatus == RsvpStatus.MAYBE) {
            event.decrementMaybeCount();
        }

        // Increment new status
        if (newStatus == RsvpStatus.GOING) {
            event.incrementGoingCount();
        } else if (newStatus == RsvpStatus.MAYBE) {
            event.incrementMaybeCount();
        }
    }

    private AttendeeResponse mapToAttendeeResponse(EventAttendee attendee) {
        UserSummary userSummary = userServiceClient.getUserSummary(attendee.getUserId());

        return AttendeeResponse.builder()
                .id(attendee.getId())
                .eventId(attendee.getEvent().getId())
                .userId(attendee.getUserId())
                .rsvpStatus(attendee.getRsvpStatus())
                .rsvpAt(attendee.getRsvpAt())
                .checkedInAt(attendee.getCheckedInAt())
                .guestCount(attendee.getGuestCount())
                .isHost(attendee.getIsHost())
                .isCoHost(attendee.getIsCoHost())
                .note(attendee.getNote())
                .user(userSummary)
                .build();
    }
}