package com.billboard.social.event.repository;

import com.billboard.social.event.entity.EventAttendee;
import com.billboard.social.event.entity.enums.RsvpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventAttendeeRepository extends JpaRepository<EventAttendee, UUID> {

    Optional<EventAttendee> findByEventIdAndUserId(UUID eventId, UUID userId);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    Page<EventAttendee> findByEventIdAndRsvpStatus(UUID eventId, RsvpStatus status, Pageable pageable);

    @Query("SELECT a FROM EventAttendee a WHERE a.event.id = :eventId AND a.rsvpStatus = 'GOING' ORDER BY a.rsvpAt")
    Page<EventAttendee> findGoingAttendees(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("SELECT a FROM EventAttendee a WHERE a.event.id = :eventId AND a.rsvpStatus = 'MAYBE' ORDER BY a.rsvpAt")
    Page<EventAttendee> findMaybeAttendees(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("SELECT a FROM EventAttendee a WHERE a.event.id = :eventId AND a.rsvpStatus = 'INVITED' ORDER BY a.invitedAt DESC")
    Page<EventAttendee> findInvitedAttendees(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("SELECT a FROM EventAttendee a WHERE a.event.id = :eventId AND a.checkedInAt IS NOT NULL ORDER BY a.checkedInAt")
    Page<EventAttendee> findCheckedInAttendees(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("SELECT COUNT(a) FROM EventAttendee a WHERE a.event.id = :eventId AND a.rsvpStatus = 'GOING'")
    long countGoingAttendees(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(a) FROM EventAttendee a WHERE a.event.id = :eventId AND a.rsvpStatus = 'MAYBE'")
    long countMaybeAttendees(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(a) FROM EventAttendee a WHERE a.event.id = :eventId AND a.rsvpStatus = 'INVITED'")
    long countInvitedAttendees(@Param("eventId") UUID eventId);

    @Query("SELECT a.userId FROM EventAttendee a WHERE a.event.id = :eventId AND a.rsvpStatus = 'GOING'")
    List<UUID> findGoingUserIds(@Param("eventId") UUID eventId);

    @Query("SELECT a FROM EventAttendee a WHERE a.event.id = :eventId AND (a.isHost = true OR a.isCoHost = true)")
    List<EventAttendee> findHosts(@Param("eventId") UUID eventId);

    @Query("SELECT a FROM EventAttendee a WHERE a.userId = :userId AND a.rsvpStatus IN ('GOING', 'MAYBE') ORDER BY a.event.startTime")
    Page<EventAttendee> findUserUpcomingEvents(@Param("userId") UUID userId, Pageable pageable);

    void deleteByEventIdAndUserId(UUID eventId, UUID userId);

    @Query("SELECT a FROM EventAttendee a WHERE a.event.id = :eventId AND a.rsvpStatus IN ('GOING', 'MAYBE') ORDER BY a.rsvpAt DESC")
    Page<EventAttendee> findConfirmedAttendees(@Param("eventId") UUID eventId, Pageable pageable);
}