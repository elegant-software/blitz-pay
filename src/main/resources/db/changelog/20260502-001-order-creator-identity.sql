-- liquibase formatted sql

-- changeset mehdi:20260502-001-order-creator-identity
ALTER TABLE blitzpay.order_orders
    ADD COLUMN creator_type  VARCHAR(16)  NOT NULL DEFAULT 'SHOPPER',
    ADD COLUMN created_by_id VARCHAR(255) NOT NULL DEFAULT 'system';

CREATE INDEX ix_order_orders_merchant_branch_id
    ON blitzpay.order_orders (merchant_branch_id);

CREATE INDEX ix_order_orders_created_by_id
    ON blitzpay.order_orders (created_by_id);

-- rollback DROP INDEX IF EXISTS blitzpay.ix_order_orders_created_by_id;
-- rollback DROP INDEX IF EXISTS blitzpay.ix_order_orders_merchant_branch_id;
-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN created_by_id;
-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN creator_type;
