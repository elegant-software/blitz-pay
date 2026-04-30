-- liquibase formatted sql

-- changeset mehdi:20260502-002-widen-event-publication-columns
ALTER TABLE event_publication
    ALTER COLUMN serialized_event TYPE TEXT,
    ALTER COLUMN listener_id      TYPE VARCHAR(1024),
    ALTER COLUMN event_type       TYPE VARCHAR(1024);
-- rollback ALTER TABLE event_publication ALTER COLUMN serialized_event TYPE VARCHAR(255), ALTER COLUMN listener_id TYPE VARCHAR(255), ALTER COLUMN event_type TYPE VARCHAR(255);
