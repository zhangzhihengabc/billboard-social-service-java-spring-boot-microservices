package com.billboard.social.graph.repository;

import com.billboard.social.graph.entity.Share;
import com.billboard.social.graph.entity.enums.ContentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShareRepository extends JpaRepository<Share, UUID> {

    Page<Share> findByUserId(UUID userId, Pageable pageable);

    Page<Share> findByContentTypeAndContentId(ContentType contentType, UUID contentId, Pageable pageable);

    long countByContentTypeAndContentId(ContentType contentType, UUID contentId);

    @Query("SELECT s.userId FROM Share s WHERE s.contentType = :contentType AND s.contentId = :contentId")
    List<UUID> findUserIdsByContent(@Param("contentType") ContentType contentType, @Param("contentId") UUID contentId);

    boolean existsByUserIdAndContentTypeAndContentId(UUID userId, ContentType contentType, UUID contentId);

    @Query("SELECT s FROM Share s WHERE s.targetUserId = :userId AND s.isPrivateShare = true")
    Page<Share> findPrivateSharesForUser(@Param("userId") UUID userId, Pageable pageable);

    void deleteByContentTypeAndContentId(ContentType contentType, UUID contentId);
}
