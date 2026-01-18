package com.billboard.social.event.repository;

import com.billboard.social.event.entity.EventCoHost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventCoHostRepository extends JpaRepository<EventCoHost, UUID> {

    Optional<EventCoHost> findByEventIdAndUserId(UUID eventId, UUID userId);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    List<EventCoHost> findByEventId(UUID eventId);

    List<EventCoHost> findByUserId(UUID userId);

    void deleteByEventIdAndUserId(UUID eventId, UUID userId);
}
