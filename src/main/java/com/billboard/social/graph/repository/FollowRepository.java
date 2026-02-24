package com.billboard.social.graph.repository;

import com.billboard.social.graph.entity.Follow;
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
public interface FollowRepository extends JpaRepository<Follow, UUID> {

    // ==================== EXISTENCE CHECKS ====================

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    // ==================== FIND SINGLE ====================

    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    // ==================== FIND PAGINATED ====================

    /**
     * Find all users who follow a specific user (followers list)
     */
    Page<Follow> findByFollowingId(Long followingId, Pageable pageable);

    /**
     * Find all users that a specific user is following (following list)
     */
    Page<Follow> findByFollowerId(Long followerId, Pageable pageable);

    /**
     * Find all close friends of a user
     */
    Page<Follow> findByFollowerIdAndIsCloseFriendTrue(Long followerId, Pageable pageable);

    /**
     * Find all muted users
     */
    Page<Follow> findByFollowerIdAndIsMutedTrue(Long followerId, Pageable pageable);

    // ==================== FIND IDS ====================

    /**
     * Get list of user IDs that a user is following
     */
    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :followerId")
    List<Long> findFollowingIdsByFollowerId(@Param("followerId") Long followerId);

    /**
     * Get list of user IDs who follow a specific user
     */
    @Query("SELECT f.followerId FROM Follow f WHERE f.followingId = :followingId")
    List<Long> findFollowerIdsByFollowingId(@Param("followingId") Long followingId);

    /**
     * Get list of close friend IDs
     */
    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :followerId AND f.isCloseFriend = true")
    List<UUID> findCloseFriendIdsByFollowerId(@Param("followerId") Long followerId);

    // ==================== COUNT ====================

    long countByFollowerId(Long followerId);

    long countByFollowingId(Long followingId);

    @Query("SELECT COUNT(f) FROM Follow f WHERE f.followerId = :followerId AND f.isCloseFriend = true")
    long countCloseFriendsByFollowerId(@Param("followerId") Long followerId);

    // ==================== DELETE ====================

    /**
     * Hard delete a follow relationship (bypasses soft delete)
     */
    @Modifying
    @Query("DELETE FROM Follow f WHERE f.followerId = :followerId AND f.followingId = :followingId")
    void hardDelete(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    /**
     * Delete all follows by a user (when user is deleted)
     */
    @Modifying
    @Query("DELETE FROM Follow f WHERE f.followerId = :userId OR f.followingId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    // ==================== BULK CHECKS ====================

    /**
     * Check if user is following any of the given user IDs
     */
    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :followerId AND f.followingId IN :followingIds")
    List<UUID> findFollowingIdsIn(@Param("followerId") Long followerId, @Param("followingIds") List<Long> followingIds);

    /**
     * Get mutual follows (users who follow each other)
     */
    @Query("SELECT f1.followingId FROM Follow f1 " +
            "WHERE f1.followerId = :userId " +
            "AND EXISTS (SELECT 1 FROM Follow f2 WHERE f2.followerId = f1.followingId AND f2.followingId = :userId)")
    List<UUID> findMutualFollows(@Param("userId") Long userId);
}