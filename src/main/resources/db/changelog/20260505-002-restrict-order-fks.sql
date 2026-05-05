-- liquibase formatted sql

-- changeset mehdi:20260505-002-restrict-order-items-fk
-- Remediate: order_items fk_order_items_order was ON DELETE CASCADE (inline in 20260430-001).
-- Constraint was renamed in 20260502-003; current name is fk_order_items_order.
-- Recreate with RESTRICT per liquibase-best-practices.md Section 10.
ALTER TABLE blitzpay.order_items
    DROP CONSTRAINT fk_order_items_order,
    ADD CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id_fk)
        REFERENCES blitzpay.order_orders (id)
        ON DELETE RESTRICT;
-- rollback ALTER TABLE blitzpay.order_items DROP CONSTRAINT fk_order_items_order, ADD CONSTRAINT fk_order_items_order FOREIGN KEY (order_id_fk) REFERENCES blitzpay.order_orders (id) ON DELETE CASCADE;

-- changeset mehdi:20260505-003-restrict-order-payment-attempts-fk
-- Remediate: order_payment_attempts fk_order_payment_attempts_order was ON DELETE CASCADE (inline in 20260430-001).
-- Constraint was renamed in 20260502-003; current name is fk_order_payment_attempts_order.
-- Recreate with RESTRICT per liquibase-best-practices.md Section 10.
ALTER TABLE blitzpay.order_payment_attempts
    DROP CONSTRAINT fk_order_payment_attempts_order,
    ADD CONSTRAINT fk_order_payment_attempts_order
        FOREIGN KEY (order_id_fk)
        REFERENCES blitzpay.order_orders (id)
        ON DELETE RESTRICT;
-- rollback ALTER TABLE blitzpay.order_payment_attempts DROP CONSTRAINT fk_order_payment_attempts_order, ADD CONSTRAINT fk_order_payment_attempts_order FOREIGN KEY (order_id_fk) REFERENCES blitzpay.order_orders (id) ON DELETE CASCADE;