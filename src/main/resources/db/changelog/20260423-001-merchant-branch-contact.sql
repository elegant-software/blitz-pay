-- liquibase formatted sql

-- changeset mehdi:20260423-001-merchant-branch-contact
ALTER TABLE blitzpay.merchant_branches
    ADD COLUMN IF NOT EXISTS contact_full_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS contact_phone_number VARCHAR(64);
-- rollback ALTER TABLE blitzpay.merchant_branches
-- rollback     DROP COLUMN IF EXISTS contact_phone_number,
-- rollback     DROP COLUMN IF EXISTS contact_email,
-- rollback     DROP COLUMN IF EXISTS contact_full_name;
