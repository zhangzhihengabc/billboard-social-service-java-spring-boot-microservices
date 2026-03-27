package com.billboard.social.gamegroup.repository;

import com.billboard.social.gamegroup.entity.GroupTeamLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupTeamLinkRepository extends JpaRepository<GroupTeamLink, UUID> {

    List<GroupTeamLink> findByGroupId(UUID groupId);

    Optional<GroupTeamLink> findByGroupIdAndTeamId(UUID groupId, Long teamId);

    boolean existsByGroupIdAndTeamId(UUID groupId, Long teamId);

    void deleteByGroupIdAndTeamId(UUID groupId, Long teamId);
}