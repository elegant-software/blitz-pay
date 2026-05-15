-- liquibase formatted sql

-- changeset mehdi:20260515-002-order-item-price-override
ALTER TABLE blitzpay.order_items
    ADD COLUMN merchant_price_override_minor BIGINT NULL,
    ADD COLUMN overridden_by VARCHAR(255) NULL,
    ADD COLUMN overridden_at TIMESTAMPTZ NULL;

-- rollback ALTER TABLE blitzpay.order_items DROP COLUMN overridden_at;
-- rollback ALTER TABLE blitzpay.order_items DROP COLUMN overridden_by;
-- rollback ALTER TABLE blitzpay.order_items DROP COLUMN merchant_price_override_minor;
