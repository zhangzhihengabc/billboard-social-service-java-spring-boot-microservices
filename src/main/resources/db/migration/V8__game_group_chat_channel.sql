-- V8: Add chat_channel_id to game_group_profiles
-- Populated asynchronously when the chat service processes the GROUP_CHAT_REQUESTED event.

ALTER TABLE game_group_profiles
    ADD COLUMN IF NOT EXISTS chat_channel_id VARCHAR(100);

-- Partial index: only index rows where the channel has been provisioned.
CREATE INDEX IF NOT EXISTS idx_game_group_chat_channel
    ON game_group_profiles (chat_channel_id)
    WHERE chat_channel_id IS NOT NULL;
