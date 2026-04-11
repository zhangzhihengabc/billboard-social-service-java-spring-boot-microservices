package com.billboard.social.friendsfinder.repository;

import com.billboard.social.friendsfinder.entity.FriendSuggestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendSuggestionRepository extends JpaRepository<FriendSuggestion, UUID> {

    Page<FriendSuggestion> findByUserIdAndDismissedFalseOrderBySuggestionScoreDesc(
            Long userId, Pageable pageable);

    Optional<FriendSuggestion> findByUserIdAndSuggestedUserId(Long userId, Long suggestedUserId);

    @Modifying
    @Query("DELETE FROM FriendSuggestion s WHERE s.createdAt < :cutoff AND s.dismissed = false")
    int purgeOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM FriendSuggestion s WHERE " +
           "(s.userId = :userA AND s.suggestedUserId = :userB) OR " +
           "(s.userId = :userB AND s.suggestedUserId = :userA)")
    void deleteBetweenUsers(@Param("userA") Long userA, @Param("userB") Long userB);
}
