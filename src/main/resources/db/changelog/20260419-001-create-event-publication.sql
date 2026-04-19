-- liquibase formatted sql

-- changeset mehdi:20260419-001-event-publication
CREATE TABLE event_publication (
    id                      UUID            NOT NULL,
    completion_attempts     INT             NOT NULL,
    completion_date         TIMESTAMPTZ(6)  NULL,
    event_type              VARCHAR(255)    NOT NULL,
    last_resubmission_date  TIMESTAMPTZ(6)  NULL,
    listener_id             VARCHAR(255)    NOT NULL,
    publication_date        TIMESTAMPTZ(6)  NOT NULL,
    serialized_event        VARCHAR(255)    NOT NULL,
    status                  VARCHAR(255)    NULL,
    CONSTRAINT pk_event_publication PRIMARY KEY (id),
    CONSTRAINT event_publication_status_check CHECK (
        status::text = ANY (ARRAY[
            'PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'
        ]::text[])
    )
);
-- rollback DROP TABLE event_publication;