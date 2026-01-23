-- V2__remove_seed_categories.sql
-- Remove seed data inserted in V1 migration to allow fresh data entry via API

-- Clear all event categories seed data
DELETE FROM event_categories;

-- Clear all group categories seed data
DELETE FROM group_categories;