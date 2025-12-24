package com.ossn.content.forum.repository;

import com.ossn.content.forum.entity.PostVote;
import com.ossn.content.forum.entity.enums.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostVoteRepository extends JpaRepository<PostVote, UUID> {

    Optional<PostVote> findByPostIdAndUserId(UUID postId, UUID userId);

    void deleteByPostIdAndUserId(UUID postId, UUID userId);

    long countByPostIdAndVoteType(UUID postId, VoteType voteType);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);
}
