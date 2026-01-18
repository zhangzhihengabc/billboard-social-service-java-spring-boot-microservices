package com.billboard.content.feed.repository;

import com.billboard.content.feed.entity.PostReaction;
import com.billboard.content.feed.entity.enums.ReactionType;
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
public interface PostReactionRepository extends JpaRepository<PostReaction, UUID> {

    Optional<PostReaction> findByPostIdAndUserId(UUID postId, UUID userId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    void deleteByPostIdAndUserId(UUID postId, UUID userId);

    Page<PostReaction> findByPostId(UUID postId, Pageable pageable);

    Page<PostReaction> findByPostIdAndReactionType(UUID postId, ReactionType reactionType, Pageable pageable);

    long countByPostIdAndReactionType(UUID postId, ReactionType reactionType);

    @Query("SELECT r.reactionType, COUNT(r) FROM PostReaction r " +
           "WHERE r.post.id = :postId GROUP BY r.reactionType")
    List<Object[]> countReactionsByType(@Param("postId") UUID postId);

    @Query("SELECT r.userId FROM PostReaction r WHERE r.post.id = :postId")
    List<UUID> findUserIdsByPostId(@Param("postId") UUID postId);
}
