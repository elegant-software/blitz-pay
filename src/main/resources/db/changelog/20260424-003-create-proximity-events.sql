-- liquibase formatted sql

-- changeset mehdi:20260424-003-create-merchant-proximity-events
CREATE TABLE blitzpay.merchant_proximity_events (
    id                  UUID             NOT NULL,
    region_id           VARCHAR(255)     NOT NULL,
    region_type         VARCHAR(16)      NOT NULL,
    source_id           UUID             NOT NULL,
    user_token          VARCHAR(512),
    device_id           VARCHAR(255),
    event_type          VARCHAR(8)       NOT NULL,
    reported_latitude   DOUBLE PRECISION NOT NULL,
    reported_longitude  DOUBLE PRECISION NOT NULL,
    received_at         TIMESTAMPTZ      NOT NULL,
    CONSTRAINT pk_merchant_proximity_events PRIMARY KEY (id)
);
CREATE INDEX ix_merchant_proximity_events_region_event
    ON blitzpay.merchant_proximity_events (region_id, event_type, received_at DESC);
CREATE INDEX ix_merchant_proximity_events_user_region
    ON blitzpay.merchant_proximity_events (user_token, region_id, received_at DESC);
-- rollback DROP TABLE blitzpay.merchant_proximity_events;
