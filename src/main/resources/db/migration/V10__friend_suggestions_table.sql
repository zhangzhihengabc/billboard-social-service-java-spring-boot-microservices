-- V10__friend_suggestions_table.sql
-- Friend suggestions for friends-finder module

CREATE TABLE friend_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL,
    suggested_user_id BIGINT NOT NULL,
    suggestion_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    source VARCHAR(30) NOT NULL,
    game_mode VARCHAR(50),
    interaction_count INTEGER DEFAULT 0,
    mutual_friend_count INTEGER DEFAULT 0,
    dismissed BOOLEAN DEFAULT FALSE,
    dismissed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT uk_friend_suggestion UNIQUE (user_id, suggested_user_id),
    CONSTRAINT chk_suggestion_different_users CHECK (user_id != suggested_user_id)
);

CREATE INDEX idx_friend_suggestion_user ON friend_suggestions(user_id);
CREATE INDEX idx_friend_suggestion_score ON friend_suggestions(suggestion_score DESC);
CREATE INDEX idx_friend_suggestion_undismissed ON friend_suggestions(user_id)
    WHERE dismissed = FALSE;
