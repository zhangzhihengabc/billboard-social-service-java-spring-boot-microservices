-- V7: Add last_broadcast_at column to group_scrim_filters
-- Required by broadcastLfs() 5-minute cooldown check in GameGroupService

ALTER TABLE group_scrim_filters
    ADD COLUMN IF NOT EXISTS last_broadcast_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_scrim_filter_last_broadcast
    ON group_scrim_filters(last_broadcast_at)
    WHERE last_broadcast_at IS NOT NULL;
