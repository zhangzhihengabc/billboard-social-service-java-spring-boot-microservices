-- V3__add_accepting_rsvps.sql

ALTER TABLE events ADD COLUMN accepting_rsvps BOOLEAN DEFAULT true;
UPDATE events SET accepting_rsvps = true WHERE accepting_rsvps IS NULL;