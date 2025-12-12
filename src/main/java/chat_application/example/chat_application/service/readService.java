package chat_application.example.chat_application.service;

import chat_application.example.chat_application.entities.group.GroupMessageMember;
import chat_application.example.chat_application.exception.ForbiddenException;
import chat_application.example.chat_application.repository.groupMessageMemberRepository;
import chat_application.example.chat_application.utill.redisUtill;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class readService {

    private final groupMessageMemberRepository groupMessageMemberRepository;
    private final redisUtill redisUtill;

    @Transactional
    public void markMessagesAsRead(Long userId, Long roomId, Long lastReadMessageId) {

        GroupMessageMember member = groupMessageMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this room"));

        // Only update if new lastReadMessageId is greater than current
        if (member.getLastReadMessageId() == null ||
                lastReadMessageId > member.getLastReadMessageId()) {

            // Use entity's helper method
            member.markAsRead(lastReadMessageId);
            groupMessageMemberRepository.save(member);

            log.debug("User {} read up to message {} in room {}", userId, lastReadMessageId, roomId);
        }

        // Refresh TTL - user is active
        redisUtill.refreshSessionTtlForUser(userId, roomId);
    }

    /**
     * Get last read message ID for a user in a room
     */
    @Transactional
    public Long getLastReadMessageId(Long userId, Long roomId) {
        return groupMessageMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .map(GroupMessageMember::getLastReadMessageId)
                .orElse(null);
    }

    /**
     * Get unread count for a user in a room
     */
    @Transactional
    public Integer getUnreadCount(Long userId, Long roomId) {
        return groupMessageMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .map(GroupMessageMember::getUnreadCount)
                .orElse(0);
    }
}
