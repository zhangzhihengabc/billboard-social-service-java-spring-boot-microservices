package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.Friend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface friendRepository extends JpaRepository<Friend, Long> {

    /**
     * Check if two users are friends
     */
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friend f " +
            "WHERE ((f.user.id = :userId1 AND f.friend.id = :userId2) " +
            "OR (f.user.id = :userId2 AND f.friend.id = :userId1)) " +
            "AND f.status = 'ACCEPTED'")
    boolean existsFriendship(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /**
     * Get all friends of a user
     */
    @Query("SELECT CASE WHEN f.user.id = :userId THEN f.friend.id ELSE f.user.id END FROM Friend f " +
            "WHERE (f.user.id = :userId OR f.friend.id = :userId) AND f.status = 'ACCEPTED'")
    List<Long> findFriendIds(@Param("userId") Long userId);

    /**
     * Find friendship between two users
     */
    @Query("SELECT f FROM Friend f " +
            "WHERE (f.user.id = :userId1 AND f.friend.id = :userId2) " +
            "OR (f.user.id = :userId2 AND f.friend.id = :userId1)")
    Optional<Friend> findFriendship(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /**
     * Find pending friend requests for a user
     */
    @Query("SELECT f FROM Friend f WHERE f.friend.id = :userId AND f.status = 'PENDING'")
    List<Friend> findPendingRequests(@Param("userId") Long userId);

    /**
     * Count friends
     */
    @Query("SELECT COUNT(f) FROM Friend f " +
            "WHERE (f.user.id = :userId OR f.friend.id = :userId) AND f.status = 'ACCEPTED'")
    long countFriends(@Param("userId") Long userId);
}
