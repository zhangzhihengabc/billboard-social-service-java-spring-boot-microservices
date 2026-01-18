package com.billboard.social.group.repository;

import com.billboard.social.group.entity.GroupMember;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId, MemberStatus status);

    Page<GroupMember> findByGroupIdAndStatus(UUID groupId, MemberStatus status, Pageable pageable);

    Page<GroupMember> findByGroupIdAndRole(UUID groupId, MemberRole role, Pageable pageable);

    @Query("SELECT m FROM GroupMember m WHERE m.group.id = :groupId AND m.status = 'APPROVED'")
    Page<GroupMember> findApprovedMembers(@Param("groupId") UUID groupId, Pageable pageable);

    @Query("SELECT m FROM GroupMember m WHERE m.group.id = :groupId AND m.status = 'PENDING'")
    Page<GroupMember> findPendingMembers(@Param("groupId") UUID groupId, Pageable pageable);

    @Query("SELECT m FROM GroupMember m WHERE m.group.id = :groupId AND m.role IN ('ADMIN', 'OWNER')")
    List<GroupMember> findAdmins(@Param("groupId") UUID groupId);

    @Query("SELECT m FROM GroupMember m WHERE m.group.id = :groupId AND m.role IN ('MODERATOR', 'ADMIN', 'OWNER')")
    List<GroupMember> findModerators(@Param("groupId") UUID groupId);

    @Query("SELECT m.userId FROM GroupMember m WHERE m.group.id = :groupId AND m.status = 'APPROVED'")
    List<UUID> findMemberUserIds(@Param("groupId") UUID groupId);

    @Query("SELECT COUNT(m) FROM GroupMember m WHERE m.group.id = :groupId AND m.status = 'APPROVED'")
    long countApprovedMembers(@Param("groupId") UUID groupId);

    @Query("SELECT COUNT(m) FROM GroupMember m WHERE m.group.id = :groupId AND m.status = 'PENDING'")
    long countPendingMembers(@Param("groupId") UUID groupId);

    @Query("SELECT m FROM GroupMember m WHERE m.userId = :userId AND m.status = 'APPROVED'")
    Page<GroupMember> findMembershipsByUser(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT m FROM GroupMember m WHERE m.userId = :userId AND m.role IN ('ADMIN', 'OWNER')")
    List<GroupMember> findAdminMembershipsByUser(@Param("userId") UUID userId);

    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);
}
