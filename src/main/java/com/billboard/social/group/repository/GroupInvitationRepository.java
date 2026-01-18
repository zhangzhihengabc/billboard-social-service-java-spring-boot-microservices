package com.billboard.social.group.repository;

import com.billboard.social.group.entity.GroupInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, UUID> {

    Optional<GroupInvitation> findByGroupIdAndInviteeId(UUID groupId, UUID inviteeId);

    Optional<GroupInvitation> findByInviteCode(String inviteCode);

    Page<GroupInvitation> findByInviteeIdAndStatus(UUID inviteeId, String status, Pageable pageable);

    Page<GroupInvitation> findByGroupIdAndStatus(UUID groupId, String status, Pageable pageable);

    @Query("SELECT i FROM GroupInvitation i WHERE i.inviteeId = :userId AND i.status = 'PENDING' " +
           "AND (i.expiresAt IS NULL OR i.expiresAt > :now)")
    Page<GroupInvitation> findPendingInvitationsForUser(@Param("userId") UUID userId, 
                                                         @Param("now") LocalDateTime now, 
                                                         Pageable pageable);

    long countByInviteeIdAndStatus(UUID inviteeId, String status);

    @Query("SELECT i FROM GroupInvitation i WHERE i.expiresAt < :now AND i.status = 'PENDING'")
    List<GroupInvitation> findExpiredInvitations(@Param("now") LocalDateTime now);

    boolean existsByGroupIdAndInviteeIdAndStatus(UUID groupId, UUID inviteeId, String status);
}
