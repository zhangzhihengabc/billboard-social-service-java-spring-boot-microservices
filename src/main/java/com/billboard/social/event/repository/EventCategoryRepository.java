package com.billboard.social.event.repository;

import com.billboard.social.event.entity.EventCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventCategoryRepository extends JpaRepository<EventCategory, UUID> {

    Optional<EventCategory> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<EventCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    @Query("SELECT c FROM EventCategory c WHERE c.isActive = true ORDER BY c.eventCount DESC")
    List<EventCategory> findPopularCategories();
}
