package com.billboard.social.event.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.security.InputValidator;
import com.billboard.social.event.dto.request.EventRequests.*;
import com.billboard.social.event.dto.response.EventResponses.*;
import com.billboard.social.event.entity.*;
import com.billboard.social.event.entity.enums.*;
import com.billboard.social.event.event.EventEventPublisher;
import com.billboard.social.event.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final EventRsvpRepository rsvpRepository;
    private final EventCoHostRepository coHostRepository;
    private final EventCategoryRepository categoryRepository;
    private final UserServiceClient userServiceClient;
    private final EventEventPublisher eventPublisher;

    @Value("${app.event.max-user-events:100}")
    private int maxUserEvents;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    // ==================== CREATE ====================

    @Transactional
    public EventResponse createEvent(Long userId, CreateEventRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }

        // Validate inputs
        if (request.getTitle() != null) {
            InputValidator.validateName(request.getTitle(), "Title");
        }
        if (request.getDescription() != null) {
            InputValidator.validateText(request.getDescription(), "Description", 10000);
        }
        if (request.getVenueName() != null) {
            InputValidator.validateText(request.getVenueName(), "Venue name", 200);
        }
        if (request.getAddress() != null) {
            InputValidator.validateText(request.getAddress(), "Address", 500);
        }

        // Validate category if provided
        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ValidationException("Category not found with id: " + request.getCategoryId()));
        }

        // Check user event limit
        long userEventCount = eventRepository.countByHostId(userId);
        if (userEventCount >= maxUserEvents) {
            throw new ValidationException("Maximum event limit reached (" + maxUserEvents + ")");
        }

        String slug = generateSlug(request.getTitle());

        Event event = Event.builder()
                .title(request.getTitle())
                .slug(slug)
                .description(request.getDescription())
                .hostId(userId)
                .groupId(request.getGroupId())
                .categoryId(request.getCategoryId())
                .eventType(request.getEventType() != null ? request.getEventType() : EventType.IN_PERSON)
                .visibility(request.getVisibility() != null ? request.getVisibility() : EventVisibility.PUBLIC)
                .status(EventStatus.DRAFT)
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
                .recurrenceType(request.getRecurrenceType() != null ? request.getRecurrenceType() : RecurrenceType.NONE)
                .recurrenceEndDate(request.getRecurrenceEndDate())
                .allowGuests(request.getAllowGuests() != null ? request.getAllowGuests() : true)
                .guestsPerRsvp(request.getGuestsPerRsvp() != null ? request.getGuestsPerRsvp() : 1)
                .showGuestList(request.getShowGuestList() != null ? request.getShowGuestList() : true)
                .allowComments(request.getAllowComments() != null ? request.getAllowComments() : true)
                .requireApproval(request.getRequireApproval() != null ? request.getRequireApproval() : false)
                .build();

        event = eventRepository.save(event);

        // Auto-RSVP host as GOING
        EventRsvp hostRsvp = EventRsvp.builder()
                .event(event)
                .userId(userId)
                .status(RsvpStatus.GOING)
                .build();
        hostRsvp.respond(RsvpStatus.GOING);
        rsvpRepository.save(hostRsvp);

        event.incrementGoingCount();
        event = eventRepository.save(event);

        eventPublisher.publishEventCreated(event);
        log.info("Event {} created by user {}", event.getId(), userId);

        return mapToEventResponse(event, userId);
    }

    // ==================== UPDATE ====================

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public EventResponse updateEvent(Long userId, UUID eventId, UpdateEventRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }

        Event event = findEventById(eventId);
        checkEditAccess(userId, event);

        // Validate and update fields
        if (request.getTitle() != null) {
            InputValidator.validateName(request.getTitle(), "Title");
            event.setTitle(request.getTitle());
            event.setSlug(generateSlug(request.getTitle()));
        }
        if (request.getDescription() != null) {
            InputValidator.validateText(request.getDescription(), "Description", 10000);
            event.setDescription(request.getDescription());
        }
        if (request.getVenueName() != null) {
            InputValidator.validateText(request.getVenueName(), "Venue name", 200);
            event.setVenueName(request.getVenueName());
        }
        if (request.getAddress() != null) {
            InputValidator.validateText(request.getAddress(), "Address", 500);
            event.setAddress(request.getAddress());
        }
        if (request.getAcceptingRsvps() != null) {
            event.setAcceptingRsvps(request.getAcceptingRsvps());
        }

        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ValidationException("Category not found with id: " + request.getCategoryId()));
            event.setCategoryId(request.getCategoryId());
        }
        if (request.getEventType() != null) event.setEventType(request.getEventType());
        if (request.getVisibility() != null) event.setVisibility(request.getVisibility());
        if (request.getStatus() != null) event.setStatus(request.getStatus());
        if (request.getCoverImageUrl() != null) event.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getStartTime() != null) event.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) event.setEndTime(request.getEndTime());
        if (request.getTimezone() != null) event.setTimezone(request.getTimezone());
        if (request.getIsAllDay() != null) event.setIsAllDay(request.getIsAllDay());
        if (request.getCity() != null) event.setCity(request.getCity());
        if (request.getCountry() != null) event.setCountry(request.getCountry());
        if (request.getMaxAttendees() != null) event.setMaxAttendees(request.getMaxAttendees());
        if (request.getAllowGuests() != null) event.setAllowGuests(request.getAllowGuests());

        event = eventRepository.save(event);
        eventPublisher.publishEventUpdated(event);

        log.info("Event {} updated by user {}", eventId, userId);
        return mapToEventResponse(event, userId);
    }

    // ==================== PUBLISH / CANCEL / DELETE ====================

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public EventResponse publishEvent(Long userId, UUID eventId) {
        Event event = findEventById(eventId);
        checkEditAccess(userId, event);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ValidationException("Only draft events can be published");
        }

        event.setStatus(EventStatus.PUBLISHED);
        event = eventRepository.save(event);

        eventPublisher.publishEventPublished(event);
        log.info("Event {} published by user {}", eventId, userId);

        return mapToEventResponse(event, userId);
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public EventResponse cancelEvent(Long userId, UUID eventId, String reason) {
        Event event = findEventById(eventId);

        if (!event.getHostId().equals(userId)) {
            throw new ForbiddenException("Only host can cancel the event");
        }

        // Validate reason if provided
        if (reason != null && !reason.isBlank()) {
            InputValidator.validateText(reason, "Reason", 500);
        }

        event.setStatus(EventStatus.CANCELLED);
        event = eventRepository.save(event);

        eventPublisher.publishEventCancelled(event, reason);
        log.info("Event {} cancelled by user {} for reason: {}", eventId, userId, reason);

        return mapToEventResponse(event, userId);
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public void deleteEvent(Long userId, UUID eventId) {
        Event event = findEventById(eventId);

        if (!event.getHostId().equals(userId)) {
            throw new ForbiddenException("Only host can delete the event");
        }

        // Delete all related data first (cascading handled by JPA, but explicit for clarity)
        rsvpRepository.deleteByEventId(eventId);
        coHostRepository.deleteByEventId(eventId);

        // Hard delete the event
        eventRepository.delete(event);

        eventPublisher.publishEventDeleted(event);
        log.info("Event {} hard-deleted by user {}", eventId, userId);
    }

    // ==================== GET EVENT ====================

    @Transactional(readOnly = true)
    @Cacheable(value = "events", key = "#eventId")
    public EventResponse getEvent(UUID eventId, Long currentUserId) {
        Event event = findEventById(eventId);
        checkViewAccess(currentUserId, event);
        return mapToEventResponse(event, currentUserId);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventBySlug(String slug, Long currentUserId) {
        if (slug == null || slug.isBlank()) {
            throw new ValidationException("Slug is required");
        }

        String sanitizedSlug = InputValidator.sanitizeSearchQuery(slug);
        if (sanitizedSlug.isBlank()) {
            throw new ValidationException("Invalid slug format");
        }

        Event event = eventRepository.findBySlug(sanitizedSlug)
                .orElseThrow(() -> new ValidationException("Event not found with slug: " + sanitizedSlug));

        checkViewAccess(currentUserId, event);
        return mapToEventResponse(event, currentUserId);
    }

    // ==================== LIST EVENTS (with PageResponse) ====================

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> getUpcomingEvents(int page, int size) {
        Page<Event> events = eventRepository.findUpcomingPublicEvents(
                LocalDateTime.now(),
                PageRequest.of(page, size));
        return PageResponse.from(events, this::mapToEventSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> searchEvents(String query, int page, int size) {
        String sanitizedQuery = InputValidator.sanitizeSearchQuery(query);
        if (sanitizedQuery.isEmpty()) {
            return PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(page)
                    .size(size)
                    .totalElements(0)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .empty(true)
                    .build();
        }

        Page<Event> events = eventRepository.searchEvents(sanitizedQuery, PageRequest.of(page, size));
        return PageResponse.from(events, this::mapToEventSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> getPopularEvents(int page, int size) {
        Page<Event> events = eventRepository.findPopularEvents(
                LocalDateTime.now(),
                PageRequest.of(page, size));
        return PageResponse.from(events, this::mapToEventSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> getUserUpcomingEvents(Long userId, int page, int size) {
        Page<Event> events = eventRepository.findUserUpcomingEvents(
                userId,
                LocalDateTime.now(),
                PageRequest.of(page, size));
        return PageResponse.from(events, this::mapToEventSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> getHostedEvents(Long userId, int page, int size) {
        Page<Event> events = eventRepository.findEventsHostedOrCohosted(
                userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime")));
        return PageResponse.from(events, this::mapToEventSummary);
    }

    // ==================== PRIVATE HELPERS ====================

    private Event findEventById(UUID eventId) {
        if (eventId == null) {
            throw new ValidationException("Event ID is required");
        }
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ValidationException("Event not found with id: " + eventId));
    }

    private void checkEditAccess(Long userId, Event event) {
        if (event.getHostId().equals(userId)) {
            return;
        }
        Optional<EventCoHost> coHost = coHostRepository.findByEventIdAndUserId(event.getId(), userId);
        if (coHost.isPresent() && coHost.get().getCanEdit()) {
            return;
        }
        throw new ForbiddenException("No edit permission for this event");
    }

    private void checkViewAccess(Long userId, Event event) {
        if (event.getVisibility() == EventVisibility.PUBLIC) {
            return;
        }
        if (userId != null) {
            if (event.getHostId().equals(userId)) return;
            if (rsvpRepository.existsByEventIdAndUserId(event.getId(), userId)) return;
            if (coHostRepository.existsByEventIdAndUserId(event.getId(), userId)) return;
        }
        throw new ForbiddenException("No view permission for this event");
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

    private EventResponse mapToEventResponse(Event event, Long currentUserId) {
        EventResponse.EventResponseBuilder builder = EventResponse.builder()
                .id(event.getId())
                .acceptingRsvps(event.getAcceptingRsvps())
                .title(event.getTitle())
                .slug(event.getSlug())
                .description(event.getDescription())
                .hostId(event.getHostId())
                .groupId(event.getGroupId())
                .categoryId(event.getCategoryId())
                .eventType(event.getEventType())
                .visibility(event.getVisibility())
                .status(event.getStatus())
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

        // Category name
        if (event.getCategoryId() != null) {
            categoryRepository.findById(event.getCategoryId())
                    .ifPresent(cat -> builder.categoryName(cat.getName()));
        }

        // Host info (with Feign fallback)
        builder.host(fetchUserSummary(event.getHostId()));

        // Co-hosts (with Feign fallback)
        List<UserSummary> coHosts = coHostRepository.findByEventId(event.getId()).stream()
                .map(ch -> fetchUserSummary(ch.getUserId()))
                .collect(Collectors.toList());
        builder.coHosts(coHosts);

        // Current user context
        if (currentUserId != null) {
            builder.isHost(event.getHostId().equals(currentUserId));
            builder.isCoHost(coHostRepository.existsByEventIdAndUserId(event.getId(), currentUserId));
        }
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
            log.warn("Failed to fetch user summary for {}: {}", userId, e.getMessage());
            return UserSummary.builder()
                    .id(userId)
                    .username("Unknown")
                    .build();
        }
    }
}