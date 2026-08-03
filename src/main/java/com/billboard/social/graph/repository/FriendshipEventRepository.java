package com.billboard.social.graph.repository;

import com.billboard.social.graph.entity.FriendshipEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FriendshipEventRepository extends JpaRepository<FriendshipEvent, UUID> {

    List<FriendshipEvent> findByFriendshipIdOrderByCreatedAt(UUID friendshipId);

    @Query("SELECT fe FROM FriendshipEvent fe WHERE " +
           "(fe.requesterId = :userId1 AND fe.addresseeId = :userId2) OR " +
           "(fe.requesterId = :userId2 AND fe.addresseeId = :userId1) " +
           "ORDER BY fe.createdAt")
    List<FriendshipEvent> findByUserPairOrderByCreatedAt(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2);
}
