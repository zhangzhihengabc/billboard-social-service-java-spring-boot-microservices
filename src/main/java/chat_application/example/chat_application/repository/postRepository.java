package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface postRepository extends JpaRepository<Post, Long> {
    /**
     * Get owner ID by post ID (for comment pin/highlight verification)
     */
    @Query("SELECT p.owner.id FROM Post p WHERE p.id = :postId")
    Long findOwnerIdById(@Param("postId") Long postId);

    // ==================== USER WALL POSTS ====================

    /**
     * Get posts on user's wall (own posts + posts by others on their wall)
     */
    @Query("SELECT p FROM Post p WHERE p.isDeleted = false AND " +
            "(p.owner.id = :userId OR p.targetUser.id = :userId) " +
            "ORDER BY p.createdAt DESC")
    Page<Post> findUserWallPosts(@Param("userId") Long userId, Pageable pageable);

    /**
     * Get user's own posts only
     */
    Page<Post> findByOwnerIdAndIsDeletedFalseOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    /**
     * Get posts in a group
     */
    Page<Post> findByTargetGroupIdAndIsDeletedFalseOrderByCreatedAtDesc(Long groupId, Pageable pageable);

    /**
     * Get home feed posts (own + friends + groups)
     * Note: This is a simplified version. Real implementation would need friend IDs
     */
    @Query("SELECT p FROM Post p WHERE p.isDeleted = false AND " +
            "(p.owner.id = :userId OR p.owner.id IN :friendIds) " +
            "ORDER BY p.createdAt DESC")
    Page<Post> findHomeFeedPosts(@Param("userId") Long userId,
                                 @Param("friendIds") List<Long> friendIds,
                                 Pageable pageable);

    /**
     * Get posts shared from original post
     */
    List<Post> findByOriginalPostIdAndIsDeletedFalse(Long originalPostId);

    /**
     * Count shares of a post
     */
    long countByOriginalPostIdAndIsDeletedFalse(Long originalPostId);

    // ==================== SEARCH ====================

    /**
     * Search posts by content
     */
    @Query("SELECT p FROM Post p WHERE p.isDeleted = false AND " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY p.createdAt DESC")
    Page<Post> searchPosts(@Param("keyword") String keyword, Pageable pageable);

    // ==================== PINNED POSTS ====================

    /**
     * Get pinned posts for a user's wall
     */
    List<Post> findByOwnerIdAndIsPinnedTrueAndIsDeletedFalseOrderByCreatedAtDesc(Long ownerId);

    /**
     * Get pinned posts in a group
     */
    List<Post> findByTargetGroupIdAndIsPinnedTrueAndIsDeletedFalseOrderByCreatedAtDesc(Long groupId);
}
