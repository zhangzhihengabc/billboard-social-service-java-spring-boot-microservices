-- V6__audit_log.sql
-- Persisted audit trail for admin actions on game groups
-- Actions: MEMBER_BANNED, MEMBER_ROLE_CHANGED, SCRIM_FILTER_UPDATED,
--          LFS_BROADCAST, LFS_CANCELLED, TEAM_LINKED, TEAM_UNLINKED,
--          OWNERSHIP_TRANSFERRED, PROFILE_UPDATED

CREATE TABLE audit_log (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
                           actor_user_id BIGINT NOT NULL,
                           action VARCHAR(50) NOT NULL,
                           target_type VARCHAR(30),
                           target_id VARCHAR(100),
                           details TEXT,
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_group ON audit_log(group_id);
CREATE INDEX idx_audit_actor ON audit_log(actor_user_id);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_created ON audit_log(group_id, created_at DESC);
