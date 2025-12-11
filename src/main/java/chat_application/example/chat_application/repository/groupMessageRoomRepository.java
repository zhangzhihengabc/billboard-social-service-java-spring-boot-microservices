package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.GroupMessageRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface groupMessageRoomRepository extends JpaRepository<GroupMessageRoom, Long> {

    List<GroupMessageRoom> findByOwnerIdAndIsActiveTrue(Long ownerId);

    List<GroupMessageRoom> findByIsActiveTrue();
}
