package com.billboard.social.group.repository;

import com.billboard.social.group.entity.GroupCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupCategoryRepository extends JpaRepository<GroupCategory, UUID> {

    Optional<GroupCategory> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<GroupCategory> findByParentIdIsNullAndIsActiveTrueOrderByDisplayOrderAsc();

    List<GroupCategory> findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID parentId);

    @Query("SELECT c FROM GroupCategory c WHERE c.isActive = true ORDER BY c.groupCount DESC")
    List<GroupCategory> findPopularCategories();
}
