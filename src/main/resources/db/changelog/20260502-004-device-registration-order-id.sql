-- liquibase formatted sql

-- changeset mehdi:20260502-004-device-registration-order-id
ALTER TABLE blitzpay.push_device_registration
    ADD COLUMN order_id VARCHAR(64);

UPDATE blitzpay.push_device_registration dr
SET order_id = pa.order_id
FROM blitzpay.order_payment_attempts pa
WHERE dr.payment_request_id = pa.payment_request_id
  AND dr.order_id IS NULL;

ALTER TABLE blitzpay.push_device_registration
    ALTER COLUMN order_id SET NOT NULL;

CREATE INDEX ix_push_device_registration_order_id
    ON blitzpay.push_device_registration (order_id);

-- rollback DROP INDEX IF EXISTS blitzpay.ix_push_device_registration_order_id;
-- rollback ALTER TABLE blitzpay.push_device_registration DROP COLUMN IF EXISTS order_id;
