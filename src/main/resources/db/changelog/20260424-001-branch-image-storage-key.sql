-- liquibase formatted sql

-- changeset mehdi:20260424-001-branch-image-storage-key
ALTER TABLE blitzpay.merchant_branches
    ADD COLUMN IF NOT EXISTS image_storage_key VARCHAR(512);
-- rollback ALTER TABLE blitzpay.merchant_branches DROP COLUMN IF EXISTS image_storage_key;
