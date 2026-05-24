package com.billboard.social.event.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.event.dto.request.EventRequests.BulkRsvpRequest;
import com.billboard.social.event.dto.request.EventRequests.InternalCreateEventRequest;
import com.billboard.social.event.dto.response.EventResponses.BulkRsvpResult;
import com.billboard.social.event.dto.response.EventResponses.EventResponse;
import com.billboard.social.event.dto.response.EventResponses.EventSummaryResponse;
import com.billboard.social.event.entity.Event;
import com.billboard.social.event.entity.EventRsvp;
import com.billboard.social.event.entity.enums.EventStatus;
import com.billboard.social.event.entity.enums.EventType;
import com.billboard.social.event.entity.enums.EventVisibility;
import com.billboard.social.event.entity.enums.RecurrenceType;
import com.billboard.social.event.entity.enums.RsvpStatus;
import com.billboard.social.event.repository.EventCategoryRepository;
import com.billboard.social.event.repository.EventCoHostRepository;
import com.billboard.social.event.repository.EventRepository;
import com.billboard.social.event.repository.EventRsvpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternalEventService {

    private final EventRepository eventRepository;
    private final EventRsvpRepository rsvpRepository;
    private final EventCoHostRepository coHostRepository;
    private final EventCategoryRepository categoryRepository;
    private final UserServiceClient userServiceClient;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    // ==================== CREATE ====================

    @Transactional
    public EventResponse createEventInternal(InternalCreateEventRequest request) {
        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ValidationException("Category not found: " + request.getCategoryId()));
        }

        String slug = generateSlug(request.getTitle());
        EventStatus status = request.getStatus() != null ? request.getStatus() : EventStatus.DRAFT;

        Event event = Event.builder()
                .title(request.getTitle())
                .slug(slug)
                .description(request.getDescription())
                .hostId(request.getHostId())
                .organisationId(request.getOrganisationId())
                .groupId(request.getGroupId())
                .categoryId(request.getCategoryId())
                .eventType(request.getEventType() != null ? request.getEventType() : EventType.IN_PERSON)
                .visibility(request.getVisibility() != null ? request.getVisibility() : EventVisibility.PUBLIC)
                .status(status)
                .coverImageUrl(request.getCoverImageUrl())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .timezone(request.getTimezone() != null ? request.getTimezone() : "UTC")
                .isAllDay(request.getIsAllDay() != null ? request.getIsAllDay() : false)
                .venueName(request.getVenueName())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .onlineUrl(request.getOnlineUrl())
                .onlinePlatform(request.getOnlinePlatform())
                .maxAttendees(request.getMaxAttendees())
                .isTicketed(request.getIsTicketed() != null ? request.getIsTicketed() : false)
                .ticketPrice(request.getTicketPrice())
                .ticketCurrency(request.getTicketCurrency())
                .recurrenceType(RecurrenceType.NONE)
                .allowGuests(request.getAllowGuests() != null ? request.getAllowGuests() : true)
                .guestsPerRsvp(request.getGuestsPerRsvp() != null ? request.getGuestsPerRsvp() : 1)
                .showGuestList(request.getShowGuestList() != null ? request.getShowGuestList() : true)
                .allowComments(request.getAllowComments() != null ? request.getAllowComments() : true)
                .requireApproval(request.getRequireApproval() != null ? request.getRequireApproval() : false)
                .build();

        event = eventRepository.save(event);

        EventRsvp hostRsvp = EventRsvp.builder()
                .event(event)
                .userId(request.getHostId())
                .organisationId(request.getOrganisationId())
                .status(RsvpStatus.GOING)
                .build();
        hostRsvp.respond(RsvpStatus.GOING);
        rsvpRepository.save(hostRsvp);

        event.incrementGoingCount();
        event = eventRepository.save(event);

        log.info("Internal event created: eventId={} orgId={} hostId={}",
                event.getId(), request.getOrganisationId(), request.getHostId());
        return mapToEventResponse(event);
    }

    // ==================== BULK RSVP ====================

    @Transactional
    public BulkRsvpResult bulkRsvp(UUID eventId, BulkRsvpRequest request) {
        Event event = findEventById(eventId);

        if (request.getStatus() == RsvpStatus.CHECKED_IN) {
            throw new ValidationException("Cannot set CHECKED_IN via bulk RSVP");
        }

        List<Long> userIds = request.getUserIds();

        Set<Long> alreadyRsvpd = rsvpRepository.findByEventIdAndUserIdIn(eventId, userIds)
                .stream()
                .map(EventRsvp::getUserId)
                .collect(Collectors.toSet());

        int created = 0;
        int alreadyExists = alreadyRsvpd.size();

        for (Long userId : userIds) {
            if (alreadyRsvpd.contains(userId)) {
                continue;
            }
            try {
                EventRsvp rsvp = EventRsvp.builder()
                        .event(event)
                        .userId(userId)
                        .organisationId(request.getOrganisationId())
                        .status(request.getStatus())
                        .build();
                rsvp.respond(request.getStatus());
                rsvpRepository.save(rsvp);

                if (request.getStatus() == RsvpStatus.GOING) {
                    event.incrementGoingCount();
                } else if (request.getStatus() == RsvpStatus.MAYBE) {
                    event.incrementMaybeCount();
                }
                created++;
            } catch (Exception e) {
                log.warn("Failed to create RSVP for userId={} eventId={} orgId={}: {}",
                        userId, eventId, request.getOrganisationId(), e.getMessage());
                alreadyExists++;
            }
        }

        eventRepository.save(event);

        log.info("Bulk RSVP complete: eventId={} orgId={} created={} alreadyExists={}",
                eventId, request.getOrganisationId(), created, alreadyExists);
        return BulkRsvpResult.builder()
                .created(created)
                .alreadyExists(alreadyExists)
                .build();
    }

    // ==================== NON-RESPONDED USERS ====================

    @Transactional(readOnly = true)
    public List<Long> getNonRespondedUserIds(UUID eventId, Long organisationId) {
        findEventById(eventId);
        List<Long> invitedIds = rsvpRepository.findInvitedUserIds(eventId);
        log.info("Non-responded query: eventId={} orgId={} count={}", eventId, organisationId, invitedIds.size());
        return invitedIds;
    }

    // ==================== EVENTS BY ORG ====================

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> getEventsByOrg(Long organisationId,
                                                      LocalDateTime start,
                                                      LocalDateTime end,
                                                      EventStatus status,
                                                      EventType eventType) {
        List<Event> events = eventRepository.findByOrganisationIdAndDateRange(organisationId, start, end);

        return events.stream()
                .filter(e -> status == null || e.getStatus() == status)
                .filter(e -> eventType == null || e.getEventType() == eventType)
                .map(this::mapToEventSummary)
                .collect(Collectors.toList());
    }

    // ==================== CANCEL ====================

    @Transactional
    public EventResponse cancelEventInternal(UUID eventId, Long organisationId) {
        Event event = findEventById(eventId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ValidationException("Event is already cancelled");
        }

        event.setStatus(EventStatus.CANCELLED);
        event = eventRepository.save(event);

        log.info("Internal cancel: eventId={} orgId={}", eventId, organisationId);
        return mapToEventResponse(event);
    }

    // ==================== PRIVATE HELPERS ====================

    private Event findEventById(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ValidationException("Event not found: " + eventId));
    }

    private EventResponse mapToEventResponse(Event event) {
        EventResponse.EventResponseBuilder builder = EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .slug(event.getSlug())
                .description(event.getDescription())
                .hostId(event.getHostId())
                .groupId(event.getGroupId())
                .categoryId(event.getCategoryId())
                .eventType(event.getEventType())
                .visibility(event.getVisibility())
                .status(event.getStatus())
                .acceptingRsvps(event.getAcceptingRsvps())
                .coverImageUrl(event.getCoverImageUrl())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .timezone(event.getTimezone())
                .isAllDay(event.getIsAllDay())
                .venueName(event.getVenueName())
                .address(event.getAddress())
                .city(event.getCity())
                .country(event.getCountry())
                .maxAttendees(event.getMaxAttendees())
                .goingCount(event.getGoingCount())
                .maybeCount(event.getMaybeCount())
                .invitedCount(event.getInvitedCount())
                .isTicketed(event.getIsTicketed())
                .ticketPrice(event.getTicketPrice())
                .ticketCurrency(event.getTicketCurrency())
                .recurrenceType(event.getRecurrenceType())
                .allowGuests(event.getAllowGuests())
                .showGuestList(event.getShowGuestList())
                .createdAt(event.getCreatedAt());

        if (event.getCategoryId() != null) {
            categoryRepository.findById(event.getCategoryId())
                    .ifPresent(cat -> builder.categoryName(cat.getName()));
        }

        builder.host(fetchUserSummary(event.getHostId()));

        List<UserSummary> coHosts = coHostRepository.findByEventId(event.getId()).stream()
                .map(ch -> fetchUserSummary(ch.getUserId()))
                .collect(Collectors.toList());
        builder.coHosts(coHosts);

        return builder.build();
    }

    private EventSummaryResponse mapToEventSummary(Event event) {
        return EventSummaryResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .slug(event.getSlug())
                .coverImageUrl(event.getCoverImageUrl())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .venueName(event.getVenueName())
                .city(event.getCity())
                .eventType(event.getEventType())
                .goingCount(event.getGoingCount())
                .isTicketed(event.getIsTicketed())
                .ticketPrice(event.getTicketPrice())
                .ticketCurrency(event.getTicketCurrency())
                .build();
    }

    private UserSummary fetchUserSummary(Long userId) {
        try {
            return userServiceClient.getUserSummary(userId);
        } catch (Exception e) {
            log.warn("Failed to fetch user summary for userId={}: {}", userId, e.getMessage());
            return UserSummary.builder().id(userId).username("Unknown").build();
        }
    }

    private String generateSlug(String input) {
        if (input == null || input.isBlank()) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        String noWhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isEmpty()) {
            slug = UUID.randomUUID().toString().substring(0, 8);
        }
        String baseSlug = slug;
        int counter = 1;
        while (eventRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }
}
