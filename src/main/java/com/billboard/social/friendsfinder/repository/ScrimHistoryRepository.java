package com.billboard.social.friendsfinder.repository;

import com.billboard.social.friendsfinder.entity.ScrimHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScrimHistoryRepository extends JpaRepository<ScrimHistory, UUID> {

    @Query("SELECT s FROM ScrimHistory s WHERE s.userIdA = :userId OR s.userIdB = :userId " +
           "ORDER BY s.playedAt DESC")
    Page<ScrimHistory> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(s) FROM ScrimHistory s WHERE " +
           "(s.userIdA = :userA AND s.userIdB = :userB) OR " +
           "(s.userIdA = :userB AND s.userIdB = :userA)")
    int countBetweenUsers(@Param("userA") Long userA, @Param("userB") Long userB);

    @Query("SELECT DISTINCT CASE WHEN s.userIdA = :userId THEN s.userIdB ELSE s.userIdA END " +
           "FROM ScrimHistory s WHERE (s.userIdA = :userId OR s.userIdB = :userId) " +
           "AND s.playedAt > :since")
    List<Long> findRecentOpponents(@Param("userId") Long userId,
                                   @Param("since") LocalDateTime since);

    boolean existsByEsportsMatchId(Long esportsMatchId);
}
