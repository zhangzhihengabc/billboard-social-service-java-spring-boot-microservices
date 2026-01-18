package com.billboard.social.group.entity;
import com.billboard.social.common.entity.BaseEntity;

import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "group_members", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}),
    indexes = {
        @Index(name = "idx_member_group", columnList = "group_id"),
        @Index(name = "idx_member_user", columnList = "user_id"),
        @Index(name = "idx_member_status", columnList = "status"),
        @Index(name = "idx_member_role", columnList = "role")
    }
)
@SQLDelete(sql = "UPDATE group_members SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private MemberRole role = MemberRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MemberStatus status = MemberStatus.PENDING;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "muted_until")
    private LocalDateTime mutedUntil;

    @Column(name = "notifications_enabled")
    @Builder.Default
    private Boolean notificationsEnabled = true;

    @Column(name = "post_count")
    @Builder.Default
    private Integer postCount = 0;

    @Column(name = "contribution_score")
    @Builder.Default
    private Integer contributionScore = 0;

    public void approve(UUID approvedById) {
        this.status = MemberStatus.APPROVED;
        this.approvedBy = approvedById;
        this.approvedAt = LocalDateTime.now();
        this.joinedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = MemberStatus.REJECTED;
    }

    public void ban() {
        this.status = MemberStatus.BANNED;
    }

    public void leave() {
        this.status = MemberStatus.LEFT;
    }

    public boolean isApproved() {
        return status == MemberStatus.APPROVED;
    }

    public boolean isPending() {
        return status == MemberStatus.PENDING;
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN || role == MemberRole.OWNER;
    }

    public boolean isModerator() {
        return role == MemberRole.MODERATOR || isAdmin();
    }

    public boolean isMuted() {
        return mutedUntil != null && mutedUntil.isAfter(LocalDateTime.now());
    }

    public void promoteToModerator() {
        this.role = MemberRole.MODERATOR;
    }

    public void promoteToAdmin() {
        this.role = MemberRole.ADMIN;
    }

    public void demoteToMember() {
        this.role = MemberRole.MEMBER;
    }
}
