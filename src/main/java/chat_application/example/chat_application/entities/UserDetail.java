package chat_application.example.chat_application.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "chat_id", length = 100)
    private String chatId;

    @Column(name = "unique_id", length = 255)
    private String uniqueId;

    @Column(name = "allowed_devices", length = 255)
    private String allowedDevices;

    @Column(name = "MAC_address", length = 255)
    private String macAddress;

    @Column(name = "ip_address", length = 255)
    private String ipAddress;

    @Column(name = "serial_number", length = 255)
    private String serialNumber;

    @Column(name = "group_name", length = 255)
    private String groupName;

    @Column(name = "channel_permissions", length = 255)
    private String channelPermissions;

    @Column(name = "VOD_permissions", length = 255)
    private String vodPermissions;

    @Column(name = "allowed_regions", length = 255)
    private String allowedRegions;

    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;

    @Column(name = "app_version", length = 255)
    private String appVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
