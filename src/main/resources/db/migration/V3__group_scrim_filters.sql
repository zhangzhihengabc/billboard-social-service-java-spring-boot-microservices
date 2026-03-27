-- V3__group_scrim_filters.sql
-- GroupScrimFilter: persisted LFS (Looking For Scrim) search preferences per group
-- When a group broadcasts an LFS signal, this defines the match criteria

CREATE TABLE group_scrim_filters (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
                                     game_tag VARCHAR(50) NOT NULL,
                                     region VARCHAR(30),
                                     format VARCHAR(10),
                                     map_pool TEXT,
                                     min_team_size INTEGER,
                                     max_team_size INTEGER,
                                     min_elo INTEGER,
                                     max_elo INTEGER,
                                     availability_slots TEXT,
                                     is_active BOOLEAN DEFAULT FALSE,
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP,
                                     deleted_at TIMESTAMP,
                                     version BIGINT DEFAULT 0
);

CREATE INDEX idx_scrim_filter_group ON group_scrim_filters(group_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_scrim_filter_search ON group_scrim_filters(game_tag, region, is_active) WHERE deleted_at IS NULL;
