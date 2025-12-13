package chat_application.example.chat_application.service;

import chat_application.example.chat_application.dto.MessageReadInfoDTO;
import chat_application.example.chat_application.dto.UserSummaryDTO;
import chat_application.example.chat_application.dto.request.message.SendMessageRequestDTO;
import chat_application.example.chat_application.dto.response.MessageResponseDTO;
import chat_application.example.chat_application.entities.MessageLike;
import chat_application.example.chat_application.entities.User;
import chat_application.example.chat_application.entities.enums.MessageType;
import chat_application.example.chat_application.entities.group.GroupMessage;
import chat_application.example.chat_application.entities.group.GroupMessageMember;
import chat_application.example.chat_application.entities.group.GroupMessageRoom;
import chat_application.example.chat_application.exception.BadRequestException;
import chat_application.example.chat_application.exception.ForbiddenException;
import chat_application.example.chat_application.exception.ResourceNotFoundException;
import chat_application.example.chat_application.repository.*;
import chat_application.example.chat_application.utill.RedisUtill;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final GroupMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final GroupMessageRoomRepository roomRepository;
    private final GroupMessageMemberRepository memberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisUtill redisUtill;
    private final MessageLikeRepository likeRepository;

    private MessageResponseDTO buildMessageResponse(GroupMessage message) {

        UserSummaryDTO sender = UserSummaryDTO.builder()
                .id(message.getSender().getId())
                .username(message.getSender().getName())
                .isOnline(true)
                .build();

        MessageResponseDTO response = MessageResponseDTO.builder()
                .id(message.getId())
                .roomId(message.getRoom().getId())
                .sender(sender)
                .content(message.getContent())
                .messageType(message.getMessageType())
                // Attachment
                .attachmentUrl(message.getAttachmentUrl())
                .attachmentType(message.getAttachmentType())
                .attachmentName(message.getAttachmentName())
                .attachmentSize(message.getAttachmentSize())
                // Reactions
                .likeCount(message.getLikeCount())
                // Metadata
                .isEdited(message.getIsEdited())
                .editedAt(message.getEditedAt())
                .isDeleted(message.getIsDeleted())
                .isPinned(message.getIsPinned())
                .pinnedAt(message.getPinnedAt())
                .isSystemMessage(message.getIsSystemMessage())
                .createdAt(message.getCreatedAt())
                .build();

        // Reply info - use setters (from @Data)
        if (message.getReplyTo() != null) {
            response.setReplyToId(message.getReplyTo().getId());
            response.setReplyToContent(message.getReplyTo().getContent());
            response.setReplyToSenderName(message.getReplyTo().getSender().getName());
        }

        return response;
    }

    /**
     * Save message to database
     * Called from WebSocketMessageController
     */
    @Transactional
    public MessageResponseDTO saveMessage(SendMessageRequestDTO request) {

        log.info("Saving message from user {} in room {}", request.getSenderId(), request.getRoomId());

        User sender = userRepository.findById(request.getSenderId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getSenderId()));

        GroupMessageRoom room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", request.getRoomId()));

        if (!memberRepository.existsByRoomIdAndUserId(request.getRoomId(), request.getSenderId())) {
            throw new ForbiddenException("You are not a member of this room");
        }

        // Handle reply
        GroupMessage replyTo = null;
        if (request.getReplyToId() != null) {
            replyTo = messageRepository.findById(request.getReplyToId())
                    .orElseThrow(() -> new ResourceNotFoundException("Reply message", request.getReplyToId()));
        }

        // Build message entity
        GroupMessage message = GroupMessage.builder()
                .room(room)
                .sender(sender)
                .content(request.getContent())
                .messageType(request.getMessageType() != null ? request.getMessageType() : MessageType.TEXT)
                .attachmentUrl(request.getAttachmentUrl())
                .attachmentType(request.getAttachmentType())
                .attachmentName(request.getAttachmentName())
                .attachmentSize(request.getAttachmentSize())
                .replyTo(replyTo)
                .isSystemMessage(Boolean.TRUE.equals(request.getIsSystemMessage()))
                .isEdited(false)
                .isDeleted(false)
                .isPinned(false)
                .likeCount(0)
                .build();

        message = messageRepository.save(message);

        // Update room's message count
        room.incrementMessageCount();
        roomRepository.save(room);

        // Refresh TTL - user is active
        redisUtill.refreshSessionTtlForUser(request.getSenderId(), request.getRoomId());

        log.info("Message {} saved in room {}", message.getId(), request.getRoomId());

        return buildMessageResponse(message);
    }

    /**
     * Broadcast message to room subscribers
     * Call AFTER transaction commits
     */
    public void broadcastMessage(Long roomId, MessageResponseDTO message) {
        String destination = "/topic/room/" + roomId + "/messages";

        try {
            messagingTemplate.convertAndSend(destination, message);
            log.debug("Message {} broadcast to {}", message.getId(), destination);
        } catch (Exception e) {
            log.error("Failed to broadcast message: {}", e.getMessage(), e);
        }
    }

    /**
     * Get messages for a room (HTTP - for loading history)
     */
    @Transactional
    public Page<MessageResponseDTO> getMessages(Long roomId, Long userId, int page, int size) {

        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ForbiddenException("You are not a member of this room");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<GroupMessage> messages = messageRepository.findByRoomIdAndIsDeletedFalseOrderByCreatedAtDesc(roomId, pageable);

        return messages.map(this::buildMessageResponse);
    }

    /**
     * Get new messages after reconnect (HTTP)
     */
    @Transactional
    public List<MessageResponseDTO> getNewMessages(Long roomId, Long userId, Long lastMessageId) {

        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ForbiddenException("You are not a member of this room");
        }

        List<GroupMessage> messages = messageRepository.findNewMessages(roomId, lastMessageId);

        return messages.stream()
                .map(this::buildMessageResponse)
                .toList();
    }

    /**
     * Edit a message (HTTP)
     * Uses entity's edit() helper method
     */
    @Transactional
    public MessageResponseDTO editMessage(Long messageId, Long userId, String newContent) {

        GroupMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        if (!message.getSender().getId().equals(userId)) {
            throw new ForbiddenException("You can only edit your own messages");
        }

        // Use entity's helper method
        message.edit(newContent);
        message = messageRepository.save(message);

        log.info("Message {} edited by user {}", messageId, userId);

        return buildMessageResponse(message);
    }

    /**
     * Delete a message (HTTP)
     * Uses entity's softDelete() helper method
     */
    @Transactional
    public MessageResponseDTO deleteMessage(Long messageId, Long userId) {

        GroupMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        if (!message.getSender().getId().equals(userId)) {
            throw new ForbiddenException("You can only delete your own messages");
        }

        // Use entity's helper method
        message.softDelete(userId);
        message = messageRepository.save(message);

        log.info("Message {} deleted by user {}", messageId, userId);

        return buildMessageResponse(message);
    }

    /**
     * Pin a message (HTTP)
     */
    @Transactional
    public MessageResponseDTO pinMessage(Long messageId, Long userId, Long roomId) {

        GroupMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        // Verify user is member
        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ForbiddenException("You are not a member of this room");
        }

        // Use entity's helper method
        message.pin(userId);
        message = messageRepository.save(message);

        log.info("Message {} pinned by user {}", messageId, userId);

        return buildMessageResponse(message);
    }

    /**
     * Unpin a message (HTTP)
     */
    @Transactional
    public MessageResponseDTO unpinMessage(Long messageId, Long userId, Long roomId) {

        GroupMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ForbiddenException("You are not a member of this room");
        }

        // Use entity's helper method
        message.unpin();
        message = messageRepository.save(message);

        log.info("Message {} unpinned by user {}", messageId, userId);

        return buildMessageResponse(message);
    }

    /**
     * Like a message (HTTP)
     */
    @Transactional
    public MessageResponseDTO likeMessage(Long messageId, Long userId) {

        if (likeRepository.existsByMessageIdAndUserId(messageId, userId)) {
            throw new BadRequestException("You have already liked this message");
        }

        GroupMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Create like record
        MessageLike like = MessageLike.builder()
                .message(message)
                .user(user)
                .build();
        likeRepository.save(like);

        message.incrementLikes();
        message = messageRepository.save(message);

        log.info("Message {} liked by user {}", messageId, userId);

        return buildMessageResponse(message);
    }

    /**
     * Unlike a message (HTTP)
     */
    @Transactional
    public MessageResponseDTO unlikeMessage(Long messageId, Long userId) {

        MessageLike like = likeRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseThrow(() -> new BadRequestException("You haven't liked this message"));

        GroupMessage message = like.getMessage();

        // Delete like record
        likeRepository.delete(like);

        // Decrement like count
        message.decrementLikes();
        message = messageRepository.save(message);

        log.info("Message {} unliked by user {}", messageId, userId);

        return buildMessageResponse(message);
    }

    /**
     * Get read info for a message - who viewed and who didn't
     */
    @Transactional
    public MessageReadInfoDTO getMessageReadInfo(Long messageId, Long userId) {

        GroupMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        Long roomId = message.getRoom().getId();

        memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BadRequestException("You are not a member of this room"));

        List<GroupMessageMember> viewedMembers = memberRepository.findMembersWhoReadMessage(roomId, messageId);

        List<GroupMessageMember> notViewedMembers = memberRepository.findMembersWhoNotReadMessage(roomId, messageId);

        Long senderId = message.getSender().getId();
        notViewedMembers = notViewedMembers.stream()
                .filter(m -> !m.getUser().getId().equals(senderId))
                .toList();

        viewedMembers = viewedMembers.stream()
                .filter(m -> !m.getUser().getId().equals(senderId))
                .toList();

        List<MessageReadInfoDTO.UserReadStatus> viewedBy = viewedMembers.stream()
                .map(m -> MessageReadInfoDTO.UserReadStatus.builder()
                        .userId(m.getUser().getId())
                        .username(m.getUser().getName())
                        .lastReadMessageId(m.getLastReadMessageId())
                        .build())
                .toList();

        List<MessageReadInfoDTO.UserReadStatus> notViewedBy = notViewedMembers.stream()
                .map(m -> MessageReadInfoDTO.UserReadStatus.builder()
                        .userId(m.getUser().getId())
                        .username(m.getUser().getName())
                        .lastReadMessageId(m.getLastReadMessageId())
                        .build())
                .toList();

        return MessageReadInfoDTO.builder()
                .messageId(messageId)
                .roomId(roomId)
                .totalMembers(viewedBy.size() + notViewedBy.size())
                .viewedCount(viewedBy.size())
                .notViewedCount(notViewedBy.size())
                .viewedBy(viewedBy)
                .notViewedBy(notViewedBy)
                .build();
    }
}
