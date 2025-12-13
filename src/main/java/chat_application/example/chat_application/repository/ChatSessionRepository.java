package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.chatEntities.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByUserIdAndRoomIdAndIsActiveTrue(Long userId, Long roomId);

    List<ChatSession> findByUserIdAndIsActiveTrue(Long userId);

    @Query("SELECT s FROM ChatSession s WHERE s.room.id = :roomId AND s.isActive = true " +
            "AND s.presenceStatus != 'OFFLINE'")
    List<ChatSession> findOnlineUsersInRoom(@Param("roomId") Long roomId);

    Optional<ChatSession> findByWebsocketIdAndIsActiveTrue(String websocketId);
}
