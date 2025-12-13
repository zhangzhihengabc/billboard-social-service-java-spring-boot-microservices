package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.MessageLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageLikeRepository extends JpaRepository<MessageLike, Long> {

    // Check if user already liked this message
    boolean existsByMessageIdAndUserId(Long messageId, Long userId);

    // Find specific like (for unlike)
    Optional<MessageLike> findByMessageIdAndUserId(Long messageId, Long userId);
}
