package com.billboard.social.event.repository;

import com.billboard.social.event.entity.EventCoHost;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventCoHostRepository extends JpaRepository<EventCoHost, UUID> {

    Optional<EventCoHost> findByEventIdAndUserId(UUID eventId, Long userId);

    boolean existsByEventIdAndUserId(UUID eventId, Long userId);

    List<EventCoHost> findByEventId(UUID eventId);

    // For hard delete
    @Modifying
    @Query("DELETE FROM EventCoHost c WHERE c.event.id = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);

    List<EventCoHost> findByUserId(Long userId);

    void deleteByEventIdAndUserId(UUID eventId, Long userId);
}
