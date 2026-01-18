package com.billboard.social.event.repository;

import com.billboard.social.event.entity.Event;
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
public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Event> findByHostId(UUID hostId, Pageable pageable);

    Page<Event> findByGroupId(UUID groupId, Pageable pageable);

    Page<Event> findByCategoryId(UUID categoryId, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.visibility = 'PUBLIC' AND e.status = 'PUBLISHED' " +
           "AND e.startTime > :now ORDER BY e.startTime ASC")
    Page<Event> findUpcomingPublicEvents(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.visibility IN ('PUBLIC') AND e.status = 'PUBLISHED' " +
           "AND (LOWER(e.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(e.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Event> searchEvents(@Param("query") String query, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.visibility = 'PUBLIC' AND e.status = 'PUBLISHED' " +
           "AND e.startTime > :now ORDER BY e.goingCount DESC")
    Page<Event> findPopularEvents(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.city = :city AND e.visibility = 'PUBLIC' " +
           "AND e.status = 'PUBLISHED' AND e.startTime > :now")
    Page<Event> findEventsByCity(@Param("city") String city, @Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.startTime BETWEEN :start AND :end " +
           "AND e.visibility = 'PUBLIC' AND e.status = 'PUBLISHED'")
    Page<Event> findEventsBetween(@Param("start") LocalDateTime start, 
                                   @Param("end") LocalDateTime end, Pageable pageable);

    @Query("SELECT e FROM Event e JOIN EventRsvp r ON e.id = r.event.id " +
           "WHERE r.userId = :userId AND r.status IN ('GOING', 'MAYBE') AND e.startTime > :now")
    Page<Event> findUserUpcomingEvents(@Param("userId") UUID userId, @Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT e FROM Event e JOIN EventRsvp r ON e.id = r.event.id " +
           "WHERE r.userId = :userId AND r.status IN ('GOING', 'MAYBE') AND e.endTime < :now")
    Page<Event> findUserPastEvents(@Param("userId") UUID userId, @Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.startTime BETWEEN :start AND :end " +
           "AND e.status = 'PUBLISHED'")
    List<Event> findEventsForReminders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT e FROM Event e WHERE e.hostId = :userId OR " +
           "EXISTS (SELECT c FROM EventCoHost c WHERE c.event = e AND c.userId = :userId)")
    Page<Event> findEventsHostedOrCohosted(@Param("userId") UUID userId, Pageable pageable);

    long countByHostId(UUID hostId);
}
