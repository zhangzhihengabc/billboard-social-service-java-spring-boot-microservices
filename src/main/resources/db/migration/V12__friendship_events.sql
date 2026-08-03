-- V12__friendship_events.sql
-- Append-only audit log for friendship status transitions.
-- No updated_at, deleted_at, or version — rows are never modified.

CREATE TABLE friendship_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    friendship_id   UUID        NOT NULL REFERENCES friendships(id),
    requester_id    BIGINT      NOT NULL,
    addressee_id    BIGINT      NOT NULL,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20) NOT NULL,
    actor_user_id   BIGINT      NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_friendship_event_friendship ON friendship_events(friendship_id);
CREATE INDEX idx_friendship_event_pair       ON friendship_events(requester_id, addressee_id);

-- Extend the CHECK constraint to include the new UNFRIENDED terminal status.
ALTER TABLE friendships DROP CONSTRAINT chk_friendship_status;
ALTER TABLE friendships ADD CONSTRAINT chk_friendship_status
    CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'BLOCKED', 'CANCELLED', 'UNFRIENDED'));
