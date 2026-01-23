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

    // Find by group and invitee (unique constraint lookup)
    Optional<GroupInvitation> findByGroupIdAndInviteeId(UUID groupId, UUID inviteeId);

    // Find by invite code (for link-based invites)
    Optional<GroupInvitation> findByInviteCode(String inviteCode);

    // Find pending invitations for a user (not expired)
    @Query("SELECT i FROM GroupInvitation i WHERE i.inviteeId = :userId AND i.status = 'PENDING' " +
            "AND (i.expiresAt IS NULL OR i.expiresAt > :now)")
    Page<GroupInvitation> findPendingInvitationsForUser(@Param("userId") UUID userId,
                                                        @Param("now") LocalDateTime now,
                                                        Pageable pageable);

    // Find all invitations for a user (any status)
    Page<GroupInvitation> findByInviteeId(UUID inviteeId, Pageable pageable);

    // Find invitations sent by a group (for admin view)
    @Query("SELECT i FROM GroupInvitation i WHERE i.group.id = :groupId AND i.status = 'PENDING' " +
            "AND (i.expiresAt IS NULL OR i.expiresAt > :now)")
    Page<GroupInvitation> findPendingInvitationsByGroup(@Param("groupId") UUID groupId,
                                                        @Param("now") LocalDateTime now,
                                                        Pageable pageable);

    // Find all invitations for a group
    Page<GroupInvitation> findByGroupId(UUID groupId, Pageable pageable);

    // Count pending invitations for user
    @Query("SELECT COUNT(i) FROM GroupInvitation i WHERE i.inviteeId = :userId AND i.status = 'PENDING' " +
            "AND (i.expiresAt IS NULL OR i.expiresAt > :now)")
    long countPendingInvitationsForUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    // Check if pending invitation exists
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM GroupInvitation i " +
            "WHERE i.group.id = :groupId AND i.inviteeId = :inviteeId AND i.status = 'PENDING' " +
            "AND (i.expiresAt IS NULL OR i.expiresAt > :now)")
    boolean existsPendingInvitation(@Param("groupId") UUID groupId,
                                    @Param("inviteeId") UUID inviteeId,
                                    @Param("now") LocalDateTime now);

    // Find expired invitations (for cleanup job)
    @Query("SELECT i FROM GroupInvitation i WHERE i.expiresAt < :now AND i.status = 'PENDING'")
    List<GroupInvitation> findExpiredInvitations(@Param("now") LocalDateTime now);

    // Delete by group (for group deletion cascade)
    void deleteByGroupId(UUID groupId);
}