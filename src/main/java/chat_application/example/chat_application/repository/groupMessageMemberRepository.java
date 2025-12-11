package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.GroupMessageMember;
import chat_application.example.chat_application.entities.GroupMessageRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface groupMessageMemberRepository extends JpaRepository<GroupMessageMember, Long> {

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    Optional<GroupMessageMember> findByRoomIdAndUserId(Long roomId, Long userId);

    List<GroupMessageMember> findByRoomId(Long roomId);

    @Query("SELECT m.room FROM GroupMessageMember m WHERE m.user.id = :userId")
    List<GroupMessageRoom> findRoomsByUserId(@Param("userId") Long userId);

    // Members who have read this message (lastReadMessageId >= messageId)
    @Query("SELECT m FROM GroupMessageMember m WHERE m.room.id = :roomId AND m.lastReadMessageId >= :messageId")
    List<GroupMessageMember> findMembersWhoReadMessage(@Param("roomId") Long roomId, @Param("messageId") Long messageId);

    // Members who have NOT read this message (lastReadMessageId < messageId OR lastReadMessageId IS NULL)
    @Query("SELECT m FROM GroupMessageMember m WHERE m.room.id = :roomId AND (m.lastReadMessageId < :messageId OR m.lastReadMessageId IS NULL)")
    List<GroupMessageMember> findMembersWhoNotReadMessage(@Param("roomId") Long roomId, @Param("messageId") Long messageId);
}
