package com.billboard.social.group.entity;
import com.billboard.social.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "group_invitations", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "invitee_id"}),
    indexes = {
        @Index(name = "idx_invitation_group", columnList = "group_id"),
        @Index(name = "idx_invitation_invitee", columnList = "invitee_id"),
        @Index(name = "idx_invitation_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupInvitation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "inviter_id", nullable = false)
    private UUID inviterId;

    @Column(name = "invitee_id")
    private UUID inviteeId;

    @Column(name = "invitee_email", length = 255)
    private String inviteeEmail;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "invite_code", length = 50)
    private String inviteCode;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    public void accept() {
        this.status = "ACCEPTED";
        this.acceptedAt = LocalDateTime.now();
    }

    public void decline() {
        this.status = "DECLINED";
        this.declinedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isPending() {
        return "PENDING".equals(status) && !isExpired();
    }
}
