package com.billboard.social.graph.repository;

import com.billboard.social.graph.entity.Invitation;
import com.billboard.social.graph.entity.enums.InvitationType;
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
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByInviterIdAndInviteeIdAndInvitationTypeAndTargetId(
        UUID inviterId, UUID inviteeId, InvitationType invitationType, UUID targetId);

    Optional<Invitation> findByInviteCode(String inviteCode);

    Page<Invitation> findByInviteeIdAndStatus(UUID inviteeId, String status, Pageable pageable);

    Page<Invitation> findByInviterIdAndInvitationType(UUID inviterId, InvitationType invitationType, Pageable pageable);

    @Query("SELECT i FROM Invitation i WHERE i.inviteeId = :userId AND i.status = 'PENDING' AND " +
           "(i.expiresAt IS NULL OR i.expiresAt > :now)")
    Page<Invitation> findPendingInvitationsForUser(@Param("userId") UUID userId, 
                                                    @Param("now") LocalDateTime now, 
                                                    Pageable pageable);

    @Query("SELECT i FROM Invitation i WHERE i.targetId = :targetId AND i.invitationType = :type AND i.status = 'PENDING'")
    List<Invitation> findPendingInvitationsForTarget(@Param("targetId") UUID targetId, 
                                                      @Param("type") InvitationType type);

    long countByInviteeIdAndStatus(UUID inviteeId, String status);

    @Query("SELECT i FROM Invitation i WHERE i.expiresAt < :now AND i.status = 'PENDING'")
    List<Invitation> findExpiredInvitations(@Param("now") LocalDateTime now);

    boolean existsByInviterIdAndInviteeIdAndInvitationTypeAndTargetIdAndStatus(
        UUID inviterId, UUID inviteeId, InvitationType invitationType, UUID targetId, String status);
}
