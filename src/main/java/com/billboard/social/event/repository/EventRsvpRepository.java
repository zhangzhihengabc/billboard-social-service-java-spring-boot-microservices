package com.billboard.social.event.repository;

import com.billboard.social.event.entity.EventRsvp;
import com.billboard.social.event.entity.enums.RsvpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRsvpRepository extends JpaRepository<EventRsvp, UUID> {

    // ==================== FIND METHODS ====================

    Optional<EventRsvp> findByEventIdAndUserId(UUID eventId, UUID userId);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    List<EventRsvp> findByEventId(UUID eventId);

    @Query("SELECT r FROM EventRsvp r WHERE r.event.id = :eventId")
    Page<EventRsvp> findByEventIdPageable(@Param("eventId") UUID eventId, Pageable pageable);

    Page<EventRsvp> findByEventIdAndStatus(UUID eventId, RsvpStatus status, Pageable pageable);

    @Query("SELECT r FROM EventRsvp r WHERE r.event.id = :eventId AND r.status IN :statuses")
    Page<EventRsvp> findByEventIdAndStatusIn(
            @Param("eventId") UUID eventId,
            @Param("statuses") List<RsvpStatus> statuses,
            Pageable pageable);

    // ==================== COUNT METHODS ====================

    long countByEventIdAndStatus(UUID eventId, RsvpStatus status);

    @Query("SELECT COUNT(r) FROM EventRsvp r WHERE r.event.id = :eventId AND r.status IN ('GOING', 'MAYBE')")
    long countAttendeesByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(r) FROM EventRsvp r WHERE r.event.id = :eventId AND r.status = 'GOING'")
    long countGoingByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(r) FROM EventRsvp r WHERE r.event.id = :eventId AND r.checkedInAt IS NOT NULL")
    long countCheckedInByEventId(@Param("eventId") UUID eventId);

    // ==================== ID QUERIES ====================

    @Query("SELECT r.userId FROM EventRsvp r WHERE r.event.id = :eventId AND r.status = 'GOING'")
    List<UUID> findGoingUserIds(@Param("eventId") UUID eventId);

    @Query("SELECT r.userId FROM EventRsvp r WHERE r.event.id = :eventId AND r.status = 'MAYBE'")
    List<UUID> findMaybeUserIds(@Param("eventId") UUID eventId);

    @Query("SELECT r.userId FROM EventRsvp r WHERE r.event.id = :eventId AND r.status IN ('GOING', 'MAYBE')")
    List<UUID> findAttendingUserIds(@Param("eventId") UUID eventId);

    // ==================== DELETE METHODS (Hard Delete) ====================

    @Modifying
    @Query("DELETE FROM EventRsvp r WHERE r.event.id = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);

    @Modifying
    @Query("DELETE FROM EventRsvp r WHERE r.event.id = :eventId AND r.userId = :userId")
    void deleteByEventIdAndUserId(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    // ==================== USER'S RSVPS ====================

    @Query("SELECT r FROM EventRsvp r WHERE r.userId = :userId AND r.status IN ('GOING', 'MAYBE')")
    Page<EventRsvp> findUserUpcomingRsvps(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT r FROM EventRsvp r WHERE r.userId = :userId")
    Page<EventRsvp> findByUserId(@Param("userId") UUID userId, Pageable pageable);
}