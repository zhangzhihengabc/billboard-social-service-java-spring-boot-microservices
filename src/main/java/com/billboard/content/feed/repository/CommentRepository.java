package com.billboard.content.feed.repository;

import com.billboard.content.feed.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    // Top-level comments for a post
    Page<Comment> findByPostIdAndParentIsNullOrderByCreatedAtDesc(UUID postId, Pageable pageable);

    // Replies to a comment
    Page<Comment> findByParentIdOrderByCreatedAtAsc(UUID parentId, Pageable pageable);

    // All comments by a user
    Page<Comment> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    // Count comments for a post
    long countByPostId(UUID postId);

    // Count replies for a comment
    long countByParentId(UUID parentId);

    // Pinned comments
    List<Comment> findByPostIdAndIsPinnedTrueOrderByCreatedAtDesc(UUID postId);

    // Recent comments
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId " +
           "AND c.parent IS NULL ORDER BY c.likeCount DESC, c.createdAt DESC")
    Page<Comment> findTopComments(@Param("postId") UUID postId, Pageable pageable);
}
