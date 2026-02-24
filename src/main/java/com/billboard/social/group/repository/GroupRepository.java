package com.billboard.social.group.repository;

import com.billboard.social.group.entity.Group;
import com.billboard.social.group.entity.enums.GroupType;
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
public interface GroupRepository extends JpaRepository<Group, UUID> {

    Optional<Group> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Group> findByOwnerId(Long ownerId, Pageable pageable);

    Page<Group> findByGroupType(GroupType groupType, Pageable pageable);

    Page<Group> findByCategoryId(UUID categoryId, Pageable pageable);

    List<Group> findByGroupTypeOrderByMemberCountDesc(GroupType groupType);

    @Query("SELECT g FROM Group g WHERE g.groupType IN ('PUBLIC', 'CLOSED') " +
           "AND (LOWER(g.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(g.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Group> searchGroups(@Param("query") String query, Pageable pageable);

    @Query("SELECT g FROM Group g WHERE g.groupType IN ('PUBLIC', 'CLOSED') " +
           "ORDER BY g.memberCount DESC")
    Page<Group> findPopularGroups(Pageable pageable);

    @Query("SELECT g FROM Group g WHERE g.isFeatured = true AND g.groupType IN ('PUBLIC', 'CLOSED')")
    Page<Group> findFeaturedGroups(Pageable pageable);

    @Query("SELECT g FROM Group g JOIN GroupMember m ON g.id = m.group.id " +
           "WHERE m.userId = :userId AND m.status = 'APPROVED'")
    Page<Group> findGroupsByMember(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT g.id FROM Group g JOIN GroupMember m ON g.id = m.group.id " +
           "WHERE m.userId = :userId AND m.status = 'APPROVED'")
    List<UUID> findGroupIdsByMember(@Param("userId") Long userId);

    @Query("SELECT COUNT(g) FROM Group g JOIN GroupMember m ON g.id = m.group.id " +
           "WHERE m.userId = :userId AND m.status = 'APPROVED'")
    long countGroupsByMember(@Param("userId") Long userId);

    @Query("SELECT g FROM Group g WHERE g.categoryId = :categoryId AND g.groupType IN ('PUBLIC', 'CLOSED') " +
           "ORDER BY g.memberCount DESC")
    Page<Group> findByCategoryIdOrderByPopularity(@Param("categoryId") UUID categoryId, Pageable pageable);
}
