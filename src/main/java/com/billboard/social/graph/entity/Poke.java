package com.billboard.social.graph.entity;
import com.billboard.social.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pokes", indexes = {
    @Index(name = "idx_poke_poker", columnList = "poker_id"),
    @Index(name = "idx_poke_poked", columnList = "poked_id"),
    @Index(name = "idx_poke_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Poke extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "poker_id", nullable = false)
    private Long pokerId;

    @Column(name = "poked_id", nullable = false)
    private Long pokedId;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "poked_back_at")
    private LocalDateTime pokedBackAt;

    @Column(name = "poke_count")
    @Builder.Default
    private Integer pokeCount = 1;

    public void pokeBack() {
        this.pokedBackAt = LocalDateTime.now();
        this.isActive = false;
    }

    public void incrementPokeCount() {
        this.pokeCount++;
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
