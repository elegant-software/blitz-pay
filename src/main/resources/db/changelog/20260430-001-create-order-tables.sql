-- liquibase formatted sql

-- changeset mehdi:20260430-001-create-order-tables
CREATE TABLE blitzpay.order_orders (
    id UUID PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    merchant_application_id UUID NOT NULL,
    merchant_branch_id UUID NULL,
    status VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total_amount_minor BIGINT NOT NULL,
    item_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ NULL,
    last_payment_request_id UUID NULL,
    last_payment_provider VARCHAR(32) NULL
);

CREATE UNIQUE INDEX ux_order_orders_order_id
    ON blitzpay.order_orders (order_id);

CREATE INDEX ix_order_orders_merchant_application_id
    ON blitzpay.order_orders (merchant_application_id);

CREATE INDEX ix_order_orders_status
    ON blitzpay.order_orders (status);

CREATE TABLE blitzpay.order_items (
    id UUID PRIMARY KEY,
    order_id_fk UUID NOT NULL REFERENCES blitzpay.order_orders (id) ON DELETE CASCADE,
    merchant_product_id UUID NOT NULL,
    merchant_application_id UUID NOT NULL,
    merchant_branch_id UUID NULL,
    product_name VARCHAR(255) NOT NULL,
    product_description VARCHAR(2000) NULL,
    quantity INTEGER NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    line_total_minor BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_order_items_order_id_fk
    ON blitzpay.order_items (order_id_fk);

CREATE TABLE blitzpay.order_payment_attempts (
    id UUID PRIMARY KEY,
    order_id_fk UUID NOT NULL REFERENCES blitzpay.order_orders (id) ON DELETE CASCADE,
    order_id VARCHAR(64) NOT NULL,
    payment_request_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_order_payment_attempts_payment_request_id
    ON blitzpay.order_payment_attempts (payment_request_id);

CREATE INDEX ix_order_payment_attempts_order_id
    ON blitzpay.order_payment_attempts (order_id);

-- rollback DROP INDEX IF EXISTS blitzpay.ix_order_payment_attempts_order_id;
-- rollback DROP INDEX IF EXISTS blitzpay.ux_order_payment_attempts_payment_request_id;
-- rollback DROP TABLE IF EXISTS blitzpay.order_payment_attempts;
-- rollback DROP INDEX IF EXISTS blitzpay.ix_order_items_order_id_fk;
-- rollback DROP TABLE IF EXISTS blitzpay.order_items;
-- rollback DROP INDEX IF EXISTS blitzpay.ix_order_orders_status;
-- rollback DROP INDEX IF EXISTS blitzpay.ix_order_orders_merchant_application_id;
-- rollback DROP INDEX IF EXISTS blitzpay.ux_order_orders_order_id;
-- rollback DROP TABLE IF EXISTS blitzpay.order_orders;
