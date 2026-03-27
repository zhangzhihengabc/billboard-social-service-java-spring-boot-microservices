package com.billboard.social.gamegroup.repository;

import com.billboard.social.gamegroup.entity.GameAccountLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GameAccountLinkRepository extends JpaRepository<GameAccountLink, UUID> {

    Optional<GameAccountLink> findByUserIdAndGameTagAndVerificationStatus(Long userId, String gameTag, String status);

    // Find existing record by exact account ID regardless of status
    Optional<GameAccountLink> findByUserIdAndGameTagAndGameAccountId(Long userId, String gameTag, String gameAccountId);

    List<GameAccountLink> findByUserId(Long userId);

    boolean existsByUserIdAndGameTagAndVerificationStatus(Long userId, String gameTag, String status);
}