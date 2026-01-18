package com.billboard.content.forum.repository;

import com.billboard.content.forum.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    @Query("SELECT p FROM Post p WHERE p.topic.id = :topicId AND p.parent IS NULL AND p.isHidden = false " +
           "ORDER BY p.createdAt ASC")
    Page<Post> findByTopicIdOrderByCreatedAt(@Param("topicId") UUID topicId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.parent.id = :parentId AND p.isHidden = false " +
           "ORDER BY p.createdAt ASC")
    List<Post> findReplies(@Param("parentId") UUID parentId);

    Page<Post> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.topic.id = :topicId AND p.isSolution = true")
    List<Post> findSolutions(@Param("topicId") UUID topicId);

    @Query("SELECT p FROM Post p WHERE p.topic.id = :topicId ORDER BY p.score DESC, p.createdAt ASC")
    Page<Post> findByTopicOrderByScore(@Param("topicId") UUID topicId, Pageable pageable);

    long countByTopicId(UUID topicId);

    long countByAuthorId(UUID authorId);

    @Query("SELECT p FROM Post p WHERE p.topic.forum.id = :forumId ORDER BY p.createdAt DESC")
    Page<Post> findRecentByForum(@Param("forumId") UUID forumId, Pageable pageable);
}
