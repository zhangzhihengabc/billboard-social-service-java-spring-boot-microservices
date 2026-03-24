package com.billboard.social.gamegroup.repository;

import com.billboard.social.gamegroup.entity.GroupScrimFilter;
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
public interface GroupScrimFilterRepository extends JpaRepository<GroupScrimFilter, UUID> {

    Optional<GroupScrimFilter> findByGroupId(UUID groupId);

    boolean existsByGroupId(UUID groupId);

    List<GroupScrimFilter> findByGameTagAndRegionAndIsActiveTrue(String gameTag, String region);

    /**
     * Full LFS search query.
     * Returns active, non-deleted scrim filters matching the supplied criteria.
     * ELO range overlap: caller's (minElo, maxElo) overlaps with filter's (minElo, maxElo).
     * NULL filter fields are treated as "no restriction" (match any caller value).
     * NULL caller params skip that criterion entirely.
     */
    @Query("SELECT f FROM GroupScrimFilter f JOIN FETCH f.group g " +
            "WHERE f.isActive = true " +
            "AND f.deletedAt IS NULL " +
            "AND f.gameTag = :gameTag " +
            "AND (:region IS NULL OR f.region = :region) " +
            "AND (:format IS NULL OR f.format = :format) " +
            "AND (:minElo IS NULL OR f.maxElo IS NULL OR f.maxElo >= :minElo) " +
            "AND (:maxElo IS NULL OR f.minElo IS NULL OR f.minElo <= :maxElo)")
    Page<GroupScrimFilter> searchActiveLfsGroups(
            @Param("gameTag") String gameTag,
            @Param("region") String region,
            @Param("format") String format,
            @Param("minElo") Integer minElo,
            @Param("maxElo") Integer maxElo,
            Pageable pageable);
}