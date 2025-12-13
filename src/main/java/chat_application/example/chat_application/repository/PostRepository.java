package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    /**
     * Get owner ID by post ID (for comment pin/highlight verification)
     */
    @Query("SELECT p.owner.id FROM Post p WHERE p.id = :postId")
    Long findOwnerIdById(@Param("postId") Long postId);

    /**
     * Get posts on user's wall (own posts + posts by others on their wall)
     */
    @Query("SELECT p FROM Post p WHERE p.isDeleted = false AND " +
            "(p.owner.id = :userId OR p.targetUser.id = :userId) " +
            "ORDER BY p.createdAt DESC")
    Page<Post> findUserWallPosts(@Param("userId") Long userId, Pageable pageable);

    /**
     * Get posts in a group
     */
    Page<Post> findByTargetGroupIdAndIsDeletedFalseOrderByCreatedAtDesc(Long groupId, Pageable pageable);

}
