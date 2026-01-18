package com.billboard.content.forum.repository;

import com.billboard.content.forum.entity.TopicSubscription;
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
public interface TopicSubscriptionRepository extends JpaRepository<TopicSubscription, UUID> {

    Optional<TopicSubscription> findByTopicIdAndUserId(UUID topicId, UUID userId);

    void deleteByTopicIdAndUserId(UUID topicId, UUID userId);

    boolean existsByTopicIdAndUserId(UUID topicId, UUID userId);

    Page<TopicSubscription> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("SELECT ts.userId FROM TopicSubscription ts WHERE ts.topicId = :topicId AND ts.notifyOnReply = true")
    List<UUID> findSubscribersToNotify(@Param("topicId") UUID topicId);

    long countByTopicId(UUID topicId);
}
