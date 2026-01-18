package com.billboard.social.event.repository;

import com.billboard.social.event.entity.EventRsvp;
import com.billboard.social.event.entity.enums.RsvpStatus;
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
public interface EventRsvpRepository extends JpaRepository<EventRsvp, UUID> {

    Optional<EventRsvp> findByEventIdAndUserId(UUID eventId, UUID userId);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    Page<EventRsvp> findByEventIdAndStatus(UUID eventId, RsvpStatus status, Pageable pageable);

    Page<EventRsvp> findByEventId(UUID eventId, Pageable pageable);

    @Query("SELECT r FROM EventRsvp r WHERE r.event.id = :eventId AND r.status = 'GOING'")
    Page<EventRsvp> findGoingAttendees(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("SELECT r FROM EventRsvp r WHERE r.event.id = :eventId AND r.status = 'MAYBE'")
    Page<EventRsvp> findMaybeAttendees(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("SELECT r FROM EventRsvp r WHERE r.event.id = :eventId AND r.status = 'INVITED'")
    Page<EventRsvp> findPendingInvites(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("SELECT r.userId FROM EventRsvp r WHERE r.event.id = :eventId AND r.status = 'GOING'")
    List<UUID> findGoingUserIds(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(r) FROM EventRsvp r WHERE r.event.id = :eventId AND r.status = 'GOING'")
    long countGoingByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(r) FROM EventRsvp r WHERE r.event.id = :eventId AND r.status = 'MAYBE'")
    long countMaybeByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(r) FROM EventRsvp r WHERE r.event.id = :eventId AND r.status = 'INVITED'")
    long countInvitedByEventId(@Param("eventId") UUID eventId);

    Page<EventRsvp> findByUserId(UUID userId, Pageable pageable);

    @Query("SELECT r FROM EventRsvp r WHERE r.userId = :userId AND r.status IN ('GOING', 'MAYBE')")
    Page<EventRsvp> findUserRsvps(@Param("userId") UUID userId, Pageable pageable);

    void deleteByEventIdAndUserId(UUID eventId, UUID userId);
}
