-- V4__game_account_links.sql
-- Stores linked game accounts for users
-- Enforces requireGameAccount on GameGroupProfile during join flow

CREATE TABLE game_account_links (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    user_id BIGINT NOT NULL,
                                    game_tag VARCHAR(50) NOT NULL,
                                    game_account_id VARCHAR(100) NOT NULL,
                                    game_account_name VARCHAR(100),
                                    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                    verified_at TIMESTAMP,
                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP,
                                    deleted_at TIMESTAMP,
                                    version BIGINT DEFAULT 0,
                                    CONSTRAINT uk_user_game_account UNIQUE (user_id, game_tag, game_account_id),
                                    CONSTRAINT chk_verification_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'))
);

CREATE INDEX idx_game_account_user ON game_account_links(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_game_account_game_tag ON game_account_links(game_tag) WHERE deleted_at IS NULL;
CREATE INDEX idx_game_account_verified ON game_account_links(user_id, game_tag) WHERE verification_status = 'VERIFIED' AND deleted_at IS NULL;
