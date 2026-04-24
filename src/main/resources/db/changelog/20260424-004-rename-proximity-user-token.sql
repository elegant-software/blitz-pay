-- liquibase formatted sql

-- changeset mehdi:20260424-004-rename-proximity-user-token
DROP INDEX IF EXISTS blitzpay.ix_merchant_proximity_events_user_region;

ALTER TABLE blitzpay.merchant_proximity_events
    RENAME COLUMN user_token TO user_subject;

CREATE INDEX ix_merchant_proximity_events_user_region
    ON blitzpay.merchant_proximity_events (user_subject, region_id, received_at DESC);

-- rollback DROP INDEX IF EXISTS blitzpay.ix_merchant_proximity_events_user_region;
-- rollback ALTER TABLE blitzpay.merchant_proximity_events RENAME COLUMN user_subject TO user_token;
-- rollback CREATE INDEX ix_merchant_proximity_events_user_region
-- rollback     ON blitzpay.merchant_proximity_events (user_token, region_id, received_at DESC);
