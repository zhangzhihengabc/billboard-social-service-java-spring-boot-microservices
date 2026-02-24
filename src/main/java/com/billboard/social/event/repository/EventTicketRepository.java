package com.billboard.social.event.repository;

import com.billboard.social.event.entity.EventTicket;
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
public interface EventTicketRepository extends JpaRepository<EventTicket, UUID> {

    Optional<EventTicket> findByTicketCode(String ticketCode);

    Page<EventTicket> findByEventId(UUID eventId, Pageable pageable);

    Page<EventTicket> findByUserId(Long userId, Pageable pageable);

    List<EventTicket> findByEventIdAndUserId(UUID eventId, Long userId);

    @Query("SELECT COUNT(t) FROM EventTicket t WHERE t.event.id = :eventId AND t.status = 'VALID'")
    long countValidTicketsByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT SUM(t.quantity) FROM EventTicket t WHERE t.event.id = :eventId AND t.status IN ('VALID', 'USED')")
    Integer countTotalTicketsSold(@Param("eventId") UUID eventId);

    boolean existsByTicketCode(String ticketCode);
}
