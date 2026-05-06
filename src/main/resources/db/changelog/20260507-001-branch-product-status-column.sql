-- liquibase formatted sql

-- changeset mehdi:20260507-001-merchant-branches-status
ALTER TABLE blitzpay.merchant_branches
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'INACTIVE';
UPDATE blitzpay.merchant_branches
    SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END;
ALTER TABLE blitzpay.merchant_branches
    ALTER COLUMN status DROP DEFAULT;
-- rollback ALTER TABLE blitzpay.merchant_branches DROP COLUMN IF EXISTS status;

-- changeset mehdi:20260507-002-merchant-products-status
ALTER TABLE blitzpay.merchant_products
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'INACTIVE';
UPDATE blitzpay.merchant_products
    SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END;
ALTER TABLE blitzpay.merchant_products
    ALTER COLUMN status DROP DEFAULT;
-- rollback ALTER TABLE blitzpay.merchant_products DROP COLUMN IF EXISTS status;
