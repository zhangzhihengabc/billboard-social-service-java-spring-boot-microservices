package com.billboard.social.graph.repository;

import com.billboard.social.graph.entity.Block;
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
public interface BlockRepository extends JpaRepository<Block, UUID> {

    Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    Page<Block> findByBlockerId(Long blockerId, Pageable pageable);

    @Query("SELECT b.blockedId FROM Block b WHERE b.blockerId = :userId")
    List<Long> findBlockedUserIds(@Param("userId") Long userId);

    @Query("SELECT b.blockerId FROM Block b WHERE b.blockedId = :userId")
    List<Long> findBlockedByUserIds(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Block b " +
           "WHERE (b.blockerId = :userId1 AND b.blockedId = :userId2) OR " +
           "(b.blockerId = :userId2 AND b.blockedId = :userId1)")
    boolean isBlockedEitherWay(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    long countByBlockerId(Long blockerId);

    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
}
