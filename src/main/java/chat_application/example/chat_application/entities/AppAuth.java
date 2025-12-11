package chat_application.example.chat_application.entities;

import chat_application.example.chat_application.entities.enums.AppAuthStatus;
import chat_application.example.chat_application.entities.enums.IsStolen;
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
@Table(name = "app_auths")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AppAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "mac_id", length = 191)
    private String macId;

    @Column(name = "vendor_id", length = 255)
    private String vendorId;

    @Column(name = "serial_no", length = 191)
    private String serialNo;

    @Column(length = 191)
    private String model;

    @Column(length = 191)
    private String maker;

    @Column(name = "imei_1", length = 100)
    private String imei1;

    @Column(name = "imei_2", length = 100)
    private String imei2;

    @Column(name = "android_build", length = 100)
    private String androidBuild;

    @Column(name = "geo_group")
    private Long geoGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppAuthStatus status = AppAuthStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_stolen")
    private IsStolen isStolen = IsStolen.NO;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
