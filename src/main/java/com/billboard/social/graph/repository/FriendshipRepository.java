package com.billboard.social.graph.repository;

import com.billboard.social.graph.entity.Friendship;
import com.billboard.social.graph.entity.enums.FriendshipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requesterId = :userId1 AND f.addresseeId = :userId2) OR " +
           "(f.requesterId = :userId2 AND f.addresseeId = :userId1)")
    Optional<Friendship> findBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requesterId = :userId OR f.addresseeId = :userId) AND f.status = :status")
    Page<Friendship> findByUserIdAndStatus(@Param("userId") Long userId,
                                            @Param("status") FriendshipStatus status, 
                                            Pageable pageable);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requesterId = :userId OR f.addresseeId = :userId) AND f.status = 'ACCEPTED'")
    Page<Friendship> findAcceptedFriendships(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT f FROM Friendship f WHERE f.addresseeId = :userId AND f.status = 'PENDING'")
    Page<Friendship> findPendingRequests(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT f FROM Friendship f WHERE f.requesterId = :userId AND f.status = 'PENDING'")
    Page<Friendship> findSentRequests(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(f) FROM Friendship f WHERE " +
           "(f.requesterId = :userId OR f.addresseeId = :userId) AND f.status = 'ACCEPTED'")
    long countFriends(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN f.requesterId = :userId THEN f.addresseeId ELSE f.requesterId END " +
           "FROM Friendship f WHERE " +
           "(f.requesterId = :userId OR f.addresseeId = :userId) AND f.status = 'ACCEPTED'")
    List<Long> findFriendIds(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f WHERE " +
           "((f.requesterId = :userId1 AND f.addresseeId = :userId2) OR " +
           "(f.requesterId = :userId2 AND f.addresseeId = :userId1)) AND f.status = 'ACCEPTED'")
    boolean areFriends(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    @Query(value = "SELECT f1.friend_id FROM (" +
           "SELECT CASE WHEN requester_id = :userId1 THEN addressee_id ELSE requester_id END AS friend_id " +
           "FROM friendships WHERE (requester_id = :userId1 OR addressee_id = :userId1) AND status = 'ACCEPTED'" +
           ") f1 INNER JOIN (" +
           "SELECT CASE WHEN requester_id = :userId2 THEN addressee_id ELSE requester_id END AS friend_id " +
           "FROM friendships WHERE (requester_id = :userId2 OR addressee_id = :userId2) AND status = 'ACCEPTED'" +
           ") f2 ON f1.friend_id = f2.friend_id", nativeQuery = true)
    List<Long> findMutualFriendIds(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
