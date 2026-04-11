-- V9__scrim_history_table.sql
-- Scrim history for friends-finder module

CREATE TABLE scrim_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id_a BIGINT NOT NULL,
    user_id_b BIGINT NOT NULL,
    esports_match_id BIGINT,
    game_mode VARCHAR(50),
    match_quality_score DOUBLE PRECISION,
    played_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_scrim_history_user_a ON scrim_history(user_id_a);
CREATE INDEX idx_scrim_history_user_b ON scrim_history(user_id_b);
CREATE INDEX idx_scrim_history_pair ON scrim_history(user_id_a, user_id_b);
CREATE INDEX idx_scrim_history_game_mode ON scrim_history(game_mode);
CREATE UNIQUE INDEX idx_scrim_history_match_id ON scrim_history(esports_match_id)
    WHERE esports_match_id IS NOT NULL;
