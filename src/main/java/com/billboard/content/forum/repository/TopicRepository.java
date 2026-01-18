package com.billboard.content.forum.repository;

import com.billboard.content.forum.entity.Topic;
import com.billboard.content.forum.entity.enums.TopicStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    Optional<Topic> findByForumIdAndSlug(UUID forumId, String slug);

    Page<Topic> findByForumIdAndStatusOrderByIsPinnedDescLastPostAtDesc(
        UUID forumId, TopicStatus status, Pageable pageable);

    Page<Topic> findByForumIdOrderByIsPinnedDescLastPostAtDesc(UUID forumId, Pageable pageable);

    Page<Topic> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    @Query("SELECT t FROM Topic t WHERE t.forum.id = :forumId AND t.status = 'OPEN' " +
           "ORDER BY t.isPinned DESC, t.isSticky DESC, t.lastPostAt DESC")
    Page<Topic> findActiveTopics(@Param("forumId") UUID forumId, Pageable pageable);

    @Query("SELECT t FROM Topic t WHERE t.isAnnouncement = true AND t.status = 'OPEN' " +
           "ORDER BY t.createdAt DESC")
    List<Topic> findAnnouncements();

    @Query("SELECT t FROM Topic t WHERE t.isFeatured = true AND t.status = 'OPEN' " +
           "ORDER BY t.score DESC, t.createdAt DESC")
    Page<Topic> findFeaturedTopics(Pageable pageable);

    @Query("SELECT t FROM Topic t WHERE t.status = 'OPEN' " +
           "ORDER BY t.lastPostAt DESC")
    Page<Topic> findRecentTopics(Pageable pageable);

    @Query("SELECT t FROM Topic t WHERE t.status = 'OPEN' AND t.createdAt > :since " +
           "ORDER BY t.score DESC, t.viewCount DESC")
    Page<Topic> findTrendingTopics(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT t FROM Topic t WHERE t.status = 'OPEN' AND " +
           "(LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.content) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY t.lastPostAt DESC")
    Page<Topic> searchTopics(@Param("query") String query, Pageable pageable);

    @Query("SELECT t FROM Topic t WHERE t.tags LIKE %:tag% AND t.status = 'OPEN' " +
           "ORDER BY t.lastPostAt DESC")
    Page<Topic> findByTag(@Param("tag") String tag, Pageable pageable);

    long countByForumId(UUID forumId);

    long countByAuthorId(UUID authorId);
}
