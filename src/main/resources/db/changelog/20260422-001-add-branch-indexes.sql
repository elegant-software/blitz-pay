-- liquibase formatted sql

-- changeset mehdi:20260422-001-add-branch-indexes
CREATE INDEX IF NOT EXISTS ix_merchant_products_branch_active ON blitzpay.merchant_products (merchant_branch_id, active);
CREATE INDEX IF NOT EXISTS ix_merchant_branches_application_active ON blitzpay.merchant_branches (merchant_application_id, active);
-- rollback DROP INDEX IF EXISTS ix_merchant_products_branch_active;
-- rollback DROP INDEX IF EXISTS ix_merchant_branches_application_active;

