package com.billboard.content.forum.repository;

import com.billboard.content.forum.entity.Forum;
import com.billboard.content.forum.entity.enums.ForumType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForumRepository extends JpaRepository<Forum, UUID> {

    Optional<Forum> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT f FROM Forum f WHERE f.parent IS NULL ORDER BY f.displayOrder, f.name")
    List<Forum> findRootForums();

    List<Forum> findByParentIdOrderByDisplayOrder(UUID parentId);

    Page<Forum> findByForumTypeOrderByDisplayOrder(ForumType forumType, Pageable pageable);

    List<Forum> findByGroupIdOrderByDisplayOrder(UUID groupId);

    @Query("SELECT f FROM Forum f WHERE f.isLocked = false ORDER BY f.displayOrder")
    List<Forum> findActiveForums();

    @Query("SELECT f FROM Forum f WHERE f.parent IS NULL AND f.groupId IS NULL ORDER BY f.displayOrder")
    List<Forum> findMainForums();
}
