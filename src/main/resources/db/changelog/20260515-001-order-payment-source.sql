-- liquibase formatted sql

-- changeset mehdi:20260515-001-order-payment-source
ALTER TABLE blitzpay.order_orders
    ADD COLUMN payment_source VARCHAR(32) NULL,
    ADD COLUMN settlement_note VARCHAR(2000) NULL;

-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN settlement_note;
-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN payment_source;
