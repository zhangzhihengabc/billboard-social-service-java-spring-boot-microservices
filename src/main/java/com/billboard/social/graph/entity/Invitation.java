package com.billboard.social.graph.entity;
import com.billboard.social.common.entity.BaseEntity;

import com.billboard.social.graph.entity.enums.InvitationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invitations", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"inviter_id", "invitee_id", "invitation_type", "target_id"}),
    indexes = {
        @Index(name = "idx_invitation_inviter", columnList = "inviter_id"),
        @Index(name = "idx_invitation_invitee", columnList = "invitee_id"),
        @Index(name = "idx_invitation_type", columnList = "invitation_type"),
        @Index(name = "idx_invitation_status", columnList = "status")
    }
)
@SQLDelete(sql = "UPDATE invitations SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "inviter_id", nullable = false)
    private Long inviterId;

    @Column(name = "invitee_id")
    private Long inviteeId;

    @Column(name = "invitee_email", length = 255)
    private String inviteeEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "invitation_type", nullable = false, length = 20)
    private InvitationType invitationType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    @Column(name = "invite_code", length = 50)
    private String inviteCode;

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
