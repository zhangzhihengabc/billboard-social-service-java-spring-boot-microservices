-- V2__game_group_profiles.sql
-- GameGroupProfile: one-to-one extension of groups table
-- Stores esports-specific metadata that does not belong in a general-purpose group

CREATE TABLE game_group_profiles (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     group_id UUID NOT NULL UNIQUE REFERENCES groups(id) ON DELETE CASCADE,
                                     game_tag VARCHAR(50),
                                     game_id UUID,
                                     region VARCHAR(30),
                                     platform VARCHAR(20),
                                     min_rank VARCHAR(30),
                                     max_rank VARCHAR(30),
                                     scrim_count INTEGER DEFAULT 0,
                                     win_rate DECIMAL(5,2),
                                     average_elo INTEGER,
                                     require_game_account BOOLEAN DEFAULT FALSE,
                                     discord_server_id VARCHAR(30),
                                     discord_channel_id VARCHAR(30),
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP,
                                     deleted_at TIMESTAMP,
                                     version BIGINT DEFAULT 0
);

CREATE INDEX idx_game_group_game_tag ON game_group_profiles(game_tag) WHERE deleted_at IS NULL;
CREATE INDEX idx_game_group_region ON game_group_profiles(region) WHERE deleted_at IS NULL;
CREATE INDEX idx_game_group_platform ON game_group_profiles(platform) WHERE deleted_at IS NULL;
