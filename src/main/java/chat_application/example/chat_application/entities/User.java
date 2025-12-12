package chat_application.example.chat_application.entities;

import chat_application.example.chat_application.entities.enums.IsCyclos;
import chat_application.example.chat_application.entities.enums.Type;
import chat_application.example.chat_application.entities.enums.UserStatus;
import chat_application.example.chat_application.entities.enums.UserTypeEnum;
import chat_application.example.chat_application.entities.group.Group;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"userDetail", "roles", "groups", "appAuths"})
@ToString(exclude = {"userDetail", "roles", "groups", "appAuths"})
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 191)
    private String name;

    @Column(nullable = false, unique = true, length = 191)
    private String email;

    @Column(nullable = false, length = 191)
    private String password;

    @Column(name = "street_address", columnDefinition = "TEXT")
    private String streetAddress;

    @Column(length = 20)
    private String dob;

    @Column(length = 20)
    private String gender;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "remember_token", length = 100)
    private String rememberToken;

    @Column(name = "created_by")
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type")
    @Builder.Default
    private UserTypeEnum userType = UserTypeEnum.NORMAL;

    @Column(name = "user_type_id", length = 50)
    private String userTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Type type = Type.NORMAL;

    @Column(name = "channel_id")
    private Long channelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_cyclos")
    @Builder.Default
    private IsCyclos isCyclos = IsCyclos.NO;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "membership_expires_at")
    private LocalDateTime membershipExpiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserDetail userDetail;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_user",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_groups",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id")
    )
    @Builder.Default
    private Set<Group> groups = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<AppAuth> appAuths = new HashSet<>();

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

}