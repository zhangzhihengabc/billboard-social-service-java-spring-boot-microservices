package com.billboard.content.forum.repository;

import com.billboard.content.forum.entity.TopicVote;
import com.billboard.content.forum.entity.enums.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopicVoteRepository extends JpaRepository<TopicVote, UUID> {

    Optional<TopicVote> findByTopicIdAndUserId(UUID topicId, UUID userId);

    void deleteByTopicIdAndUserId(UUID topicId, UUID userId);

    long countByTopicIdAndVoteType(UUID topicId, VoteType voteType);

    boolean existsByTopicIdAndUserId(UUID topicId, UUID userId);
}
