package com.billboard.social.graph.repository;

import com.billboard.social.graph.entity.Follow;
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
public interface FollowRepository extends JpaRepository<Follow, UUID> {

    Optional<Follow> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    Page<Follow> findByFollowerId(UUID followerId, Pageable pageable);

    Page<Follow> findByFollowingId(UUID followingId, Pageable pageable);

    @Query("SELECT f FROM Follow f WHERE f.followerId = :userId AND f.isCloseFriend = true")
    Page<Follow> findCloseFriends(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :userId")
    List<UUID> findFollowingIds(@Param("userId") UUID userId);

    @Query("SELECT f.followerId FROM Follow f WHERE f.followingId = :userId")
    List<UUID> findFollowerIds(@Param("userId") UUID userId);

    long countByFollowerId(UUID followerId);

    long countByFollowingId(UUID followingId);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Follow f " +
           "WHERE f.followerId = :followerId AND f.followingId = :followingId")
    boolean isFollowing(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    @Query("SELECT f1.followingId FROM Follow f1 " +
           "WHERE f1.followerId = :userId1 AND f1.followingId IN " +
           "(SELECT f2.followingId FROM Follow f2 WHERE f2.followerId = :userId2)")
    List<UUID> findMutualFollowing(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);

    void deleteByFollowerIdAndFollowingId(UUID followerId, UUID followingId);
}
