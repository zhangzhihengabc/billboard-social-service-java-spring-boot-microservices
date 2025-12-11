package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.GroupMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface groupMessageRepository extends JpaRepository<GroupMessage, Long> {

    Page<GroupMessage> findByRoomIdAndIsDeletedFalseOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    List<GroupMessage> findByRoomIdAndIsDeletedFalseOrderByCreatedAtAsc(Long roomId);

    @Query("SELECT m FROM GroupMessage m WHERE m.room.id = :roomId AND m.id > :lastMessageId AND m.isDeleted = false ORDER BY m.createdAt ASC")
    List<GroupMessage> findNewMessages(@Param("roomId") Long roomId, @Param("lastMessageId") Long lastMessageId);

    @Query("SELECT COUNT(m) FROM GroupMessage m WHERE m.room.id = :roomId AND m.id > :lastReadMessageId AND m.isDeleted = false")
    Long countUnreadMessages(@Param("roomId") Long roomId, @Param("lastReadMessageId") Long lastReadMessageId);

    Long countByRoomIdAndIsDeletedFalse(Long roomId);
}
