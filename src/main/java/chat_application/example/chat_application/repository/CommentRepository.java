package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.commentEntities.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Find comments for an entity (paginated, top-level only)
     */
    Page<Comment> findByEntityTypeAndEntityIdAndParentIsNullAndIsDeletedFalseOrderByCreatedAtDesc(
            String entityType, Long entityId, Pageable pageable);

    /**
     * Find comments for a specific object within an entity
     */
    Page<Comment> findByEntityTypeAndEntityIdAndObjectTypeAndObjectIdAndParentIsNullAndIsDeletedFalseOrderByCreatedAtDesc(
            String entityType, Long entityId, String objectType, Long objectId, Pageable pageable);

    /**
     * Find replies to a comment
     */
    Page<Comment> findByParentIdAndIsDeletedFalseOrderByCreatedAtAsc(Long parentId, Pageable pageable);

    /**
     * Find video comments by timestamp range (convenience method)
     */
    @Query("SELECT c FROM Comment c WHERE c.entityType = 'video' AND c.entityId = :videoId " +
            "AND c.timestampSeconds >= :startTime AND c.timestampSeconds <= :endTime " +
            "AND c.isDeleted = false ORDER BY c.timestampSeconds ASC")
    List<Comment> findVideoCommentsByTimeRange(
            @Param("videoId") Long videoId,
            @Param("startTime") Integer startTime,
            @Param("endTime") Integer endTime);

    /**
     * Find replay comments by timestamp range
     */
    @Query("SELECT c FROM Comment c WHERE c.entityType = 'replay' AND c.entityId = :replayId " +
            "AND c.timestampSeconds >= :startTime AND c.timestampSeconds <= :endTime " +
            "AND c.isDeleted = false ORDER BY c.timestampSeconds ASC")
    List<Comment> findReplayCommentsByTimeRange(
            @Param("replayId") Long replayId,
            @Param("startTime") Integer startTime,
            @Param("endTime") Integer endTime);

    /**
     * Find pinned comments for an entity
     */
    List<Comment> findByEntityTypeAndEntityIdAndIsPinnedTrueAndIsDeletedFalseOrderByCreatedAtDesc(
            String entityType, Long entityId);

    /**
     * Find new comments since a specific comment ID
     */
    @Query("SELECT c FROM Comment c WHERE c.entityType = :entityType AND c.entityId = :entityId " +
            "AND c.id > :afterCommentId AND c.isDeleted = false ORDER BY c.createdAt ASC")
    List<Comment> findNewComments(
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("afterCommentId") Long afterCommentId);

    /**
     * Find latest comment ID for an entity
     */
    @Query("SELECT MAX(c.id) FROM Comment c WHERE c.entityType = :entityType AND c.entityId = :entityId AND c.isDeleted = false")
    Long findLatestCommentId(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    /**
     * Search comments by content
     */
    @Query("SELECT c FROM Comment c WHERE c.entityType = :entityType AND c.entityId = :entityId " +
            "AND c.isDeleted = false AND LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Comment> searchComments(
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * Find comments mentioning a specific user
     */
    @Query("SELECT c FROM Comment c WHERE c.isDeleted = false " +
            "AND c.mentionedUserIds LIKE CONCAT('%', :userId, '%') ORDER BY c.createdAt DESC")
    Page<Comment> findCommentsMentioningUser(@Param("userId") Long userId, Pageable pageable);
}
