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

    @Transactional
    public EventResponse createEvent(UUID userId, CreateEventRequest request) {
        long userEventCount = eventRepository.countByHostId(userId);
        if (userEventCount >= maxUserEvents) {
            throw new ValidationException("Maximum event limit reached");
        }

        String slug = generateSlug(request.getTitle());

        Event event = Event.builder()
            .title(request.getTitle()).slug(slug).description(request.getDescription())
            .hostId(userId).groupId(request.getGroupId()).categoryId(request.getCategoryId())
            .eventType(request.getEventType()).visibility(request.getVisibility()).status(EventStatus.DRAFT)
            .coverImageUrl(request.getCoverImageUrl()).startTime(request.getStartTime()).endTime(request.getEndTime())
            .timezone(request.getTimezone()).isAllDay(request.getIsAllDay()).venueName(request.getVenueName())
            .address(request.getAddress()).city(request.getCity()).country(request.getCountry())
            .latitude(request.getLatitude()).longitude(request.getLongitude()).onlineUrl(request.getOnlineUrl())
            .onlinePlatform(request.getOnlinePlatform()).maxAttendees(request.getMaxAttendees())
            .isTicketed(request.getIsTicketed()).ticketPrice(request.getTicketPrice())
            .ticketCurrency(request.getTicketCurrency()).recurrenceType(request.getRecurrenceType())
            .recurrenceEndDate(request.getRecurrenceEndDate()).allowGuests(request.getAllowGuests())
            .guestsPerRsvp(request.getGuestsPerRsvp()).showGuestList(request.getShowGuestList())
            .allowComments(request.getAllowComments()).requireApproval(request.getRequireApproval())
            .build();

        event = eventRepository.save(event);

        EventRsvp hostRsvp = EventRsvp.builder().event(event).userId(userId).status(RsvpStatus.GOING).build();
        hostRsvp.respond(RsvpStatus.GOING);
        rsvpRepository.save(hostRsvp);
        event.incrementGoingCount();
        eventRepository.save(event);

        eventPublisher.publishEventCreated(event);
        log.info("Event {} created by user {}", event.getId(), userId);
        return mapToEventResponse(event, userId);
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public EventResponse updateEvent(UUID userId, UUID eventId, UpdateEventRequest request) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));
        checkEditAccess(userId, event);

        if (request.getTitle() != null) { event.setTitle(request.getTitle()); event.setSlug(generateSlug(request.getTitle())); }
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getCategoryId() != null) event.setCategoryId(request.getCategoryId());
        if (request.getEventType() != null) event.setEventType(request.getEventType());
        if (request.getVisibility() != null) event.setVisibility(request.getVisibility());
        if (request.getStatus() != null) event.setStatus(request.getStatus());
        if (request.getCoverImageUrl() != null) event.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getStartTime() != null) event.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) event.setEndTime(request.getEndTime());
        if (request.getTimezone() != null) event.setTimezone(request.getTimezone());
        if (request.getIsAllDay() != null) event.setIsAllDay(request.getIsAllDay());
        if (request.getVenueName() != null) event.setVenueName(request.getVenueName());
        if (request.getAddress() != null) event.setAddress(request.getAddress());
        if (request.getCity() != null) event.setCity(request.getCity());
        if (request.getCountry() != null) event.setCountry(request.getCountry());
        if (request.getMaxAttendees() != null) event.setMaxAttendees(request.getMaxAttendees());
        if (request.getAllowGuests() != null) event.setAllowGuests(request.getAllowGuests());

        event = eventRepository.save(event);
        eventPublisher.publishEventUpdated(event);
        return mapToEventResponse(event, userId);
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public EventResponse publishEvent(UUID userId, UUID eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));
        checkEditAccess(userId, event);
        if (event.getStatus() != EventStatus.DRAFT) throw new ValidationException("Only draft events can be published");
        event.setStatus(EventStatus.PUBLISHED);
        event = eventRepository.save(event);
        eventPublisher.publishEventPublished(event);
        return mapToEventResponse(event, userId);
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public EventResponse cancelEvent(UUID userId, UUID eventId, String reason) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));
        if (!event.getHostId().equals(userId)) throw new ForbiddenException("Only host can cancel");
        event.setStatus(EventStatus.CANCELLED);
        event = eventRepository.save(event);
        eventPublisher.publishEventCancelled(event, reason);
        return mapToEventResponse(event, userId);
    }

    @Transactional
    @CacheEvict(value = "events", key = "#eventId")
    public void deleteEvent(UUID userId, UUID eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));
        if (!event.getHostId().equals(userId)) throw new ForbiddenException("Only host can delete");
        event.softDelete();
        eventRepository.save(event);
        eventPublisher.publishEventDeleted(event);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "events", key = "#eventId")
    public EventResponse getEvent(UUID eventId, UUID currentUserId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));
        checkViewAccess(currentUserId, event);
        return mapToEventResponse(event, currentUserId);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventBySlug(String slug, UUID currentUserId) {
        Event event = eventRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Slug Not Found"));
        checkViewAccess(currentUserId, event);
        return mapToEventResponse(event, currentUserId);
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> getUpcomingEvents(int page, int size) {
        return eventRepository.findUpcomingPublicEvents(LocalDateTime.now(), PageRequest.of(page, size))
            .map(this::mapToEventSummary);
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> searchEvents(String query, int page, int size) {
        return eventRepository.searchEvents(query, PageRequest.of(page, size)).map(this::mapToEventSummary);
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> getPopularEvents(int page, int size) {
        return eventRepository.findPopularEvents(LocalDateTime.now(), PageRequest.of(page, size))
            .map(this::mapToEventSummary);
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> getUserUpcomingEvents(UUID userId, int page, int size) {
        return eventRepository.findUserUpcomingEvents(userId, LocalDateTime.now(), PageRequest.of(page, size))
            .map(this::mapToEventSummary);
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> getHostedEvents(UUID userId, int page, int size) {
        return eventRepository.findEventsHostedOrCohosted(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime")))
            .map(this::mapToEventSummary);
    }

    private void checkEditAccess(UUID userId, Event event) {
        if (event.getHostId().equals(userId)) return;
        Optional<EventCoHost> coHost = coHostRepository.findByEventIdAndUserId(event.getId(), userId);
        if (coHost.isPresent() && coHost.get().getCanEdit()) return;
        throw new ForbiddenException("No edit permission");
    }

    private void checkViewAccess(UUID userId, Event event) {
        if (event.getVisibility() == EventVisibility.PUBLIC) return;
        if (userId != null && (event.getHostId().equals(userId) || 
            rsvpRepository.existsByEventIdAndUserId(event.getId(), userId) ||
            coHostRepository.existsByEventIdAndUserId(event.getId(), userId))) return;
        throw new ForbiddenException("No view permission");
    }

    private String generateSlug(String input) {
        String noWhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("").toLowerCase(Locale.ENGLISH)
            .replaceAll("-+", "-").replaceAll("^-|-$", "");
        String baseSlug = slug;
        int counter = 1;
        while (eventRepository.existsBySlug(slug)) slug = baseSlug + "-" + counter++;
        return slug;
    }

    private EventResponse mapToEventResponse(Event event, UUID currentUserId) {
        EventResponse.EventResponseBuilder builder = EventResponse.builder()
            .id(event.getId()).title(event.getTitle()).slug(event.getSlug()).description(event.getDescription())
            .hostId(event.getHostId()).groupId(event.getGroupId()).categoryId(event.getCategoryId())
            .eventType(event.getEventType()).visibility(event.getVisibility()).status(event.getStatus())
            .coverImageUrl(event.getCoverImageUrl()).startTime(event.getStartTime()).endTime(event.getEndTime())
            .timezone(event.getTimezone()).isAllDay(event.getIsAllDay()).venueName(event.getVenueName())
            .address(event.getAddress()).city(event.getCity()).country(event.getCountry())
            .maxAttendees(event.getMaxAttendees()).goingCount(event.getGoingCount()).maybeCount(event.getMaybeCount())
            .invitedCount(event.getInvitedCount()).isTicketed(event.getIsTicketed()).ticketPrice(event.getTicketPrice())
            .ticketCurrency(event.getTicketCurrency()).recurrenceType(event.getRecurrenceType())
            .allowGuests(event.getAllowGuests()).showGuestList(event.getShowGuestList()).createdAt(event.getCreatedAt());

        if (event.getCategoryId() != null) categoryRepository.findById(event.getCategoryId())
            .ifPresent(cat -> builder.categoryName(cat.getName()));
        builder.host(userServiceClient.getUserSummary(event.getHostId()));
        builder.coHosts(coHostRepository.findByEventId(event.getId()).stream()
            .map(ch -> userServiceClient.getUserSummary(ch.getUserId())).collect(Collectors.toList()));
        if (currentUserId != null) {
            builder.isHost(event.getHostId().equals(currentUserId));
            builder.isCoHost(coHostRepository.existsByEventIdAndUserId(event.getId(), currentUserId));
            rsvpRepository.findByEventIdAndUserId(event.getId(), currentUserId)
                .ifPresent(rsvp -> builder.userRsvpStatus(rsvp.getStatus()));
        }
        return builder.build();
    }

    private EventSummaryResponse mapToEventSummary(Event event) {
        return EventSummaryResponse.builder().id(event.getId()).title(event.getTitle()).slug(event.getSlug())
            .coverImageUrl(event.getCoverImageUrl()).startTime(event.getStartTime()).endTime(event.getEndTime())
            .venueName(event.getVenueName()).city(event.getCity()).eventType(event.getEventType())
            .goingCount(event.getGoingCount()).isTicketed(event.getIsTicketed()).ticketPrice(event.getTicketPrice())
            .ticketCurrency(event.getTicketCurrency()).build();
    }
}
