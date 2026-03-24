package com.billboard.social.gamegroup.repository;

import com.billboard.social.gamegroup.entity.GameGroupProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GameGroupProfileRepository extends JpaRepository<GameGroupProfile, UUID> {

    Optional<GameGroupProfile> findByGroupId(UUID groupId);

    boolean existsByGroupId(UUID groupId);

    void deleteByGroupId(UUID groupId);

    Page<GameGroupProfile> findByGameTag(String gameTag, Pageable pageable);

    Page<GameGroupProfile> findByGameTagAndRegion(String gameTag, String region, Pageable pageable);
}