package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.group.GroupMessageMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMessageMemberRepository extends JpaRepository<GroupMessageMember, Long> {

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    Optional<GroupMessageMember> findByRoomIdAndUserId(Long roomId, Long userId);

    @Query("SELECT m FROM GroupMessageMember m WHERE m.room.id = :roomId AND m.lastReadMessageId >= :messageId")
    List<GroupMessageMember> findMembersWhoReadMessage(@Param("roomId") Long roomId, @Param("messageId") Long messageId);

    @Query("SELECT m FROM GroupMessageMember m WHERE m.room.id = :roomId AND (m.lastReadMessageId < :messageId OR m.lastReadMessageId IS NULL)")
    List<GroupMessageMember> findMembersWhoNotReadMessage(@Param("roomId") Long roomId, @Param("messageId") Long messageId);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM GroupMessageMember m " +
            "WHERE m.room.id = :roomId AND m.user.id = :userId AND m.role IN ('OWNER', 'ADMIN')")
    boolean isRoomAdmin(@Param("roomId") Long roomId, @Param("userId") Long userId);


}
