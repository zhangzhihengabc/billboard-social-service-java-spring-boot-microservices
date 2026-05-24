-- V11__organisation_id.sql
-- Add organisation_id to events, event_rsvps, event_categories for multi-tenancy (SchoolERP).
-- Nullable: existing rows have no org association; new code paths populate it.

ALTER TABLE events ADD COLUMN IF NOT EXISTS organisation_id BIGINT;
ALTER TABLE event_rsvps ADD COLUMN IF NOT EXISTS organisation_id BIGINT;
ALTER TABLE event_categories ADD COLUMN IF NOT EXISTS organisation_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_event_organisation ON events(organisation_id);
CREATE INDEX IF NOT EXISTS idx_rsvp_organisation ON event_rsvps(organisation_id);
