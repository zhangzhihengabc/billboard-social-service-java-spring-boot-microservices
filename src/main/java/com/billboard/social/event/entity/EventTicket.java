package com.billboard.social.event.entity;
import com.billboard.social.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_tickets", indexes = {
    @Index(name = "idx_ticket_event", columnList = "event_id"),
    @Index(name = "idx_ticket_user", columnList = "user_id"),
    @Index(name = "idx_ticket_code", columnList = "ticket_code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventTicket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ticket_code", nullable = false, unique = true, length = 50)
    private String ticketCode;

    @Column(name = "ticket_type", length = 50)
    @Builder.Default
    private String ticketType = "GENERAL";

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "quantity")
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "VALID";

    @Column(name = "purchased_at")
    private LocalDateTime purchasedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    public void use() {
        this.status = "USED";
        this.usedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = "CANCELLED";
    }

    public boolean isValid() {
        return "VALID".equals(status);
    }
}
