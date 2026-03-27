package com.billboard.social.gamegroup.entity;

import com.billboard.social.common.entity.BaseEntity;
import com.billboard.social.group.entity.Group;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "game_group_profiles", indexes = {
        @Index(name = "idx_game_group_game_tag", columnList = "game_tag"),
        @Index(name = "idx_game_group_region", columnList = "region"),
        @Index(name = "idx_game_group_platform", columnList = "platform")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameGroupProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(name = "game_tag", length = 50)
    private String gameTag;

    @Column(name = "game_id")
    private UUID gameId;

    @Column(name = "region", length = 30)
    private String region;

    @Column(name = "platform", length = 20)
    private String platform;

    @Column(name = "min_rank", length = 30)
    private String minRank;

    @Column(name = "max_rank", length = 30)
    private String maxRank;

    @Column(name = "scrim_count")
    @Builder.Default
    private Integer scrimCount = 0;

    @Column(name = "win_rate", precision = 5, scale = 2)
    private BigDecimal winRate;

    @Column(name = "average_elo")
    private Integer averageElo;

    @Column(name = "require_game_account")
    @Builder.Default
    private Boolean requireGameAccount = false;

    @Column(name = "discord_server_id", length = 30)
    private String discordServerId;

    @Column(name = "discord_channel_id", length = 30)
    private String discordChannelId;

    // Populated asynchronously when the chat service provisions the group channel.
    @Column(name = "chat_channel_id", length = 100)
    private String chatChannelId;
}