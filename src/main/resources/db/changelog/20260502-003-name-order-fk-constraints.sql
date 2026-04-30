-- liquibase formatted sql

-- changeset mehdi:20260502-003-name-order-fk-constraints
ALTER TABLE blitzpay.order_items
    RENAME CONSTRAINT order_items_order_id_fk_fkey TO fk_order_items_order;

ALTER TABLE blitzpay.order_payment_attempts
    RENAME CONSTRAINT order_payment_attempts_order_id_fk_fkey TO fk_order_payment_attempts_order;
-- rollback ALTER TABLE blitzpay.order_items RENAME CONSTRAINT fk_order_items_order TO order_items_order_id_fk_fkey;
-- rollback ALTER TABLE blitzpay.order_payment_attempts RENAME CONSTRAINT fk_order_payment_attempts_order TO order_payment_attempts_order_id_fk_fkey;
