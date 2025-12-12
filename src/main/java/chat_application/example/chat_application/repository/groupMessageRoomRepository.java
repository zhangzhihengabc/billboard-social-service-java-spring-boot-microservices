package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.group.GroupMessageRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface groupMessageRoomRepository extends JpaRepository<GroupMessageRoom, Long> {

    List<GroupMessageRoom> findByOwnerIdAndIsActiveTrue(Long ownerId);

    List<GroupMessageRoom> findByIsActiveTrue();

    @Query("SELECT r.owner.id FROM GroupMessageRoom r WHERE r.id = :roomId")
    Long findOwnerIdById(@Param("roomId") Long roomId);

    @Query("SELECT CASE WHEN r.owner.id = :userId THEN true ELSE false END FROM GroupMessageRoom r WHERE r.id = :roomId")
    boolean isOwner(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
