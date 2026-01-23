package com.billboard.social.group.repository;

import com.billboard.social.group.entity.GroupCategory;
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
public interface GroupCategoryRepository extends JpaRepository<GroupCategory, UUID> {

    Optional<GroupCategory> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    Page<GroupCategory> findByIsActiveTrueOrderByDisplayOrderAsc(Pageable pageable);

    List<GroupCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    Page<GroupCategory> findByParentIdIsNullAndIsActiveTrueOrderByDisplayOrderAsc(Pageable pageable);

    Page<GroupCategory> findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID parentId, Pageable pageable);

    /**
     * Search categories by name or description.
     * Uses native query with proper ESCAPE clause for special characters.
     * The service layer should escape %, _, and \ in the query before calling this.
     */
    @Query("SELECT c FROM GroupCategory c WHERE c.isActive = true AND " +
            "(LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(c.description) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY c.displayOrder ASC")
    Page<GroupCategory> searchCategories(@Param("query") String query, Pageable pageable);

    @Query("SELECT MAX(c.displayOrder) FROM GroupCategory c")
    Integer findMaxDisplayOrder();

    long countByParentId(UUID parentId);
}