package com.billboard.social.event.event;

import com.billboard.social.event.entity.*;
import com.billboard.social.event.entity.enums.RsvpStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange:social-events}")
    private String exchange;

    public void publishEventCreated(Event event) {
        Map<String, Object> eventData = createBaseEvent("event.created");
        eventData.put("eventId", event.getId());
        eventData.put("title", event.getTitle());
        eventData.put("hostId", event.getHostId());
        eventData.put("eventType", event.getEventType().name());
        eventData.put("startTime", event.getStartTime().toString());
        publish("event.created", eventData);
    }

    public void publishEventUpdated(Event event) {
        Map<String, Object> eventData = createBaseEvent("event.updated");
        eventData.put("eventId", event.getId());
        eventData.put("title", event.getTitle());
        eventData.put("hostId", event.getHostId());
        publish("event.updated", eventData);
    }

    public void publishEventPublished(Event event) {
        Map<String, Object> eventData = createBaseEvent("event.published");
        eventData.put("eventId", event.getId());
        eventData.put("title", event.getTitle());
        eventData.put("hostId", event.getHostId());
        eventData.put("startTime", event.getStartTime().toString());
        publish("event.published", eventData);
    }

    public void publishEventCancelled(Event event, String reason) {
        Map<String, Object> eventData = createBaseEvent("event.cancelled");
        eventData.put("eventId", event.getId());
        eventData.put("title", event.getTitle());
        eventData.put("hostId", event.getHostId());
        eventData.put("reason", reason);
        publish("event.cancelled", eventData);
    }

    public void publishEventDeleted(Event event) {
        Map<String, Object> eventData = createBaseEvent("event.deleted");
        eventData.put("eventId", event.getId());
        eventData.put("hostId", event.getHostId());
        publish("event.deleted", eventData);
    }

    public void publishRsvpChanged(EventRsvp rsvp, RsvpStatus oldStatus) {
        Map<String, Object> eventData = createBaseEvent("event.rsvp.changed");
        eventData.put("eventId", rsvp.getEvent().getId());
        eventData.put("userId", rsvp.getUserId());
        eventData.put("oldStatus", oldStatus != null ? oldStatus.name() : null);
        eventData.put("newStatus", rsvp.getStatus().name());
        publish("event.rsvp.changed", eventData);
    }

    public void publishRsvpChanged(EventAttendee attendee, RsvpStatus oldStatus) {
        Map<String, Object> eventData = createBaseEvent("event.rsvp.changed");
        eventData.put("eventId", attendee.getEvent().getId());
        eventData.put("userId", attendee.getUserId());
        eventData.put("oldStatus", oldStatus != null ? oldStatus.name() : null);
        eventData.put("newStatus", attendee.getRsvpStatus().name());
        publish("event.rsvp.changed", eventData);
    }

    public void publishCheckIn(EventRsvp rsvp) {
        Map<String, Object> eventData = createBaseEvent("event.checkin");
        eventData.put("eventId", rsvp.getEvent().getId());
        eventData.put("userId", rsvp.getUserId());
        publish("event.checkin", eventData);
    }

    public void publishAttendeeCheckedIn(Object attendee) {
        // Generic method for compatibility - extract event and user info as needed
        Map<String, Object> eventData = createBaseEvent("event.attendee.checkedin");
        eventData.put("attendee", attendee.toString());
        publish("event.attendee.checkedin", eventData);
    }

    public void publishInviteSent(EventRsvp rsvp) {
        Map<String, Object> eventData = createBaseEvent("event.invitation.sent");
        eventData.put("eventId", rsvp.getEvent().getId());
        eventData.put("inviteeId", rsvp.getUserId());
        publish("event.invitation.sent", eventData);
    }

    public void publishUserInvited(Object attendee, String message) {
        Map<String, Object> eventData = createBaseEvent("event.user.invited");
        eventData.put("attendee", attendee.toString());
        eventData.put("message", message);
        publish("event.user.invited", eventData);
    }

    public void publishInvitationSent(Event event, UUID inviteeId, UUID inviterId) {
        Map<String, Object> eventData = createBaseEvent("event.invitation.sent");
        eventData.put("eventId", event.getId());
        eventData.put("eventTitle", event.getTitle());
        eventData.put("inviterId", inviterId);
        eventData.put("inviteeId", inviteeId);
        publish("event.invitation.sent", eventData);
    }

    public void publishCoHostAdded(EventCoHost coHost) {
        Map<String, Object> eventData = createBaseEvent("event.cohost.added");
        eventData.put("eventId", coHost.getEvent().getId());
        eventData.put("coHostUserId", coHost.getUserId());
        publish("event.cohost.added", eventData);
    }

    public void publishCoHostRemoved(UUID eventId, UUID userId) {
        Map<String, Object> eventData = createBaseEvent("event.cohost.removed");
        eventData.put("eventId", eventId);
        eventData.put("coHostUserId", userId);
        publish("event.cohost.removed", eventData);
    }

    public void publishEventReminder(Event event, int hoursUntilStart) {
        Map<String, Object> eventData = createBaseEvent("event.reminder");
        eventData.put("eventId", event.getId());
        eventData.put("title", event.getTitle());
        eventData.put("hostId", event.getHostId());
        eventData.put("hoursUntilStart", hoursUntilStart);
        eventData.put("startTime", event.getStartTime().toString());
        publish("event.reminder", eventData);
    }

    private Map<String, Object> createBaseEvent(String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("eventId", UUID.randomUUID().toString());
        return event;
    }

    private void publish(String routingKey, Map<String, Object> event) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.debug("Published event: {} with routing key: {}", event.get("eventType"), routingKey);
        } catch (Exception e) {
            log.error("Failed to publish event: {} - {}", event.get("eventType"), e.getMessage());
        }
    }
}