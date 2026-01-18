package com.billboard.social.group.event;

import com.billboard.social.group.entity.Group;
import com.billboard.social.group.entity.GroupInvitation;
import com.billboard.social.group.entity.GroupMember;
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
public class GroupEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange:social-events}")
    private String exchange;

    public void publishGroupCreated(Group group) {
        Map<String, Object> event = createBaseEvent("group.created");
        event.put("groupId", group.getId());
        event.put("name", group.getName());
        event.put("ownerId", group.getOwnerId());
        event.put("groupType", group.getGroupType().name());
        publish("group.created", event);
    }

    public void publishGroupUpdated(Group group) {
        Map<String, Object> event = createBaseEvent("group.updated");
        event.put("groupId", group.getId());
        event.put("name", group.getName());
        publish("group.updated", event);
    }

    public void publishGroupDeleted(Group group) {
        Map<String, Object> event = createBaseEvent("group.deleted");
        event.put("groupId", group.getId());
        event.put("ownerId", group.getOwnerId());
        publish("group.deleted", event);
    }

    public void publishMemberJoined(Group group, GroupMember member) {
        Map<String, Object> event = createBaseEvent("group.member.joined");
        event.put("groupId", group.getId());
        event.put("userId", member.getUserId());
        event.put("role", member.getRole().name());
        publish("group.member.joined", event);
    }

    public void publishMemberJoined(GroupMember member) {
        publishMemberJoined(member.getGroup(), member);
    }

    public void publishJoinRequested(GroupMember member) {
        Map<String, Object> event = createBaseEvent("group.join_request.created");
        event.put("groupId", member.getGroup().getId());
        event.put("userId", member.getUserId());
        publish("group.join_request.created", event);
    }

    public void publishMemberLeft(Group group, UUID userId) {
        Map<String, Object> event = createBaseEvent("group.member.left");
        event.put("groupId", group.getId());
        event.put("userId", userId);
        publish("group.member.left", event);
    }

    public void publishMemberLeft(GroupMember member) {
        publishMemberLeft(member.getGroup(), member.getUserId());
    }

    public void publishMemberRemoved(Group group, UUID userId, UUID removedBy) {
        Map<String, Object> event = createBaseEvent("group.member.removed");
        event.put("groupId", group.getId());
        event.put("userId", userId);
        event.put("removedBy", removedBy);
        publish("group.member.removed", event);
    }

    public void publishMemberRemoved(GroupMember member, UUID removedBy) {
        publishMemberRemoved(member.getGroup(), member.getUserId(), removedBy);
    }

    public void publishMemberApproved(GroupMember member) {
        Map<String, Object> event = createBaseEvent("group.member.approved");
        event.put("groupId", member.getGroup().getId());
        event.put("userId", member.getUserId());
        event.put("approvedBy", member.getApprovedBy());
        publish("group.member.approved", event);
    }

    public void publishMemberBanned(GroupMember member, UUID bannedBy, String reason) {
        Map<String, Object> event = createBaseEvent("group.member.banned");
        event.put("groupId", member.getGroup().getId());
        event.put("userId", member.getUserId());
        event.put("bannedBy", bannedBy);
        event.put("reason", reason);
        publish("group.member.banned", event);
    }

    public void publishMemberRoleChanged(Group group, GroupMember member, String oldRole) {
        Map<String, Object> event = createBaseEvent("group.member.role_changed");
        event.put("groupId", group.getId());
        event.put("userId", member.getUserId());
        event.put("oldRole", oldRole);
        event.put("newRole", member.getRole().name());
        publish("group.member.role_changed", event);
    }

    public void publishInvitationSent(GroupInvitation invitation) {
        Map<String, Object> event = createBaseEvent("group.invitation.sent");
        event.put("invitationId", invitation.getId());
        event.put("groupId", invitation.getGroup().getId());
        event.put("inviterId", invitation.getInviterId());
        event.put("inviteeId", invitation.getInviteeId());
        publish("group.invitation.sent", event);
    }

    public void publishJoinRequestCreated(Group group, GroupMember member) {
        Map<String, Object> event = createBaseEvent("group.join_request.created");
        event.put("groupId", group.getId());
        event.put("userId", member.getUserId());
        publish("group.join_request.created", event);
    }

    public void publishJoinRequestApproved(Group group, GroupMember member, UUID approvedBy) {
        Map<String, Object> event = createBaseEvent("group.join_request.approved");
        event.put("groupId", group.getId());
        event.put("userId", member.getUserId());
        event.put("approvedBy", approvedBy);
        publish("group.join_request.approved", event);
    }

    public void publishJoinRequestRejected(Group group, UUID userId, UUID rejectedBy) {
        Map<String, Object> event = createBaseEvent("group.join_request.rejected");
        event.put("groupId", group.getId());
        event.put("userId", userId);
        event.put("rejectedBy", rejectedBy);
        publish("group.join_request.rejected", event);
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
