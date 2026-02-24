package com.billboard.social.graph.repository;

import com.billboard.social.graph.entity.Reaction;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.entity.enums.ReactionType;
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
public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    Optional<Reaction> findByUserIdAndContentTypeAndContentId(Long userId, ContentType contentType, UUID contentId);

    boolean existsByUserIdAndContentTypeAndContentId(Long userId, ContentType contentType, UUID contentId);

    Page<Reaction> findByContentTypeAndContentId(ContentType contentType, UUID contentId, Pageable pageable);

    @Query("SELECT r FROM Reaction r WHERE r.contentType = :contentType AND r.contentId = :contentId AND r.reactionType = :reactionType")
    Page<Reaction> findByContentAndReactionType(@Param("contentType") ContentType contentType, 
                                                 @Param("contentId") UUID contentId,
                                                 @Param("reactionType") ReactionType reactionType, 
                                                 Pageable pageable);

    long countByContentTypeAndContentId(ContentType contentType, UUID contentId);

    @Query("SELECT r.reactionType, COUNT(r) FROM Reaction r " +
           "WHERE r.contentType = :contentType AND r.contentId = :contentId " +
           "GROUP BY r.reactionType")
    List<Object[]> countByContentGroupedByReactionType(@Param("contentType") ContentType contentType, 
                                                        @Param("contentId") UUID contentId);

    @Query("SELECT r.userId FROM Reaction r WHERE r.contentType = :contentType AND r.contentId = :contentId")
    List<UUID> findUserIdsByContent(@Param("contentType") ContentType contentType, @Param("contentId") UUID contentId);

    Page<Reaction> findByUserId(Long userId, Pageable pageable);

    void deleteByUserIdAndContentTypeAndContentId(Long userId, ContentType contentType, UUID contentId);

    void deleteByContentTypeAndContentId(ContentType contentType, UUID contentId);
}
