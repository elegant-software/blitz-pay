-- liquibase formatted sql

-- changeset mehdi:20260425-001-push-payment-voice-context
ALTER TABLE blitzpay.push_payment_status
    ADD COLUMN payer_ref VARCHAR(512),
    ADD COLUMN order_id VARCHAR(128),
    ADD COLUMN amount_minor_units BIGINT,
    ADD COLUMN currency VARCHAR(3);
CREATE INDEX ix_push_payment_status_payer_ref
    ON blitzpay.push_payment_status (payer_ref, updated_at DESC);
-- rollback DROP INDEX IF EXISTS blitzpay.ix_push_payment_status_payer_ref;
-- rollback ALTER TABLE blitzpay.push_payment_status DROP COLUMN IF EXISTS currency;
-- rollback ALTER TABLE blitzpay.push_payment_status DROP COLUMN IF EXISTS amount_minor_units;
-- rollback ALTER TABLE blitzpay.push_payment_status DROP COLUMN IF EXISTS order_id;
-- rollback ALTER TABLE blitzpay.push_payment_status DROP COLUMN IF EXISTS payer_ref;
