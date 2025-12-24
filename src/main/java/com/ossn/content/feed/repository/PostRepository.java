package com.ossn.content.feed.repository;

import com.ossn.content.feed.entity.Post;
import com.ossn.content.feed.entity.enums.PostVisibility;
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
public interface PostRepository extends JpaRepository<Post, UUID> {

    // User's own posts
    Page<Post> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    // Wall posts (posts on a user's profile)
    Page<Post> findByWallOwnerIdOrderByCreatedAtDesc(UUID wallOwnerId, Pageable pageable);

    // Group posts
    Page<Post> findByGroupIdOrderByCreatedAtDesc(UUID groupId, Pageable pageable);

    // User feed - posts from friends
    @Query("SELECT p FROM Post p WHERE p.authorId IN :userIds " +
           "AND p.visibility IN ('PUBLIC', 'FRIENDS') " +
           "AND p.groupId IS NULL " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findFeedPosts(@Param("userIds") List<UUID> userIds, Pageable pageable);

    // Public feed
    @Query("SELECT p FROM Post p WHERE p.visibility = 'PUBLIC' " +
           "AND p.groupId IS NULL " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findPublicFeed(Pageable pageable);

    // Trending posts
    @Query("SELECT p FROM Post p WHERE p.visibility = 'PUBLIC' " +
           "AND p.createdAt > :since " +
           "ORDER BY (p.likeCount + p.loveCount + p.commentCount * 2 + p.shareCount * 3) DESC")
    Page<Post> findTrendingPosts(@Param("since") LocalDateTime since, Pageable pageable);

    // Pinned posts
    List<Post> findByWallOwnerIdAndIsPinnedTrueOrderByCreatedAtDesc(UUID wallOwnerId);

    // Search posts
    @Query("SELECT p FROM Post p WHERE p.visibility = 'PUBLIC' " +
           "AND (LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Post> searchPosts(@Param("query") String query, Pageable pageable);

    // Count user posts
    long countByAuthorId(UUID authorId);

    // Scheduled posts
    @Query("SELECT p FROM Post p WHERE p.scheduledAt IS NOT NULL " +
           "AND p.scheduledAt <= :now AND p.publishedAt IS NULL")
    List<Post> findScheduledPostsToPublish(@Param("now") LocalDateTime now);
}
