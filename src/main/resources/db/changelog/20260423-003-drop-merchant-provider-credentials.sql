-- liquibase formatted sql

-- changeset mehdi:20260423-003-drop-merchant-provider-credentials
ALTER TABLE blitzpay.merchant_applications
    DROP COLUMN IF EXISTS stripe_secret_key,
    DROP COLUMN IF EXISTS stripe_publishable_key,
    DROP COLUMN IF EXISTS braintree_merchant_id,
    DROP COLUMN IF EXISTS braintree_public_key,
    DROP COLUMN IF EXISTS braintree_private_key,
    DROP COLUMN IF EXISTS braintree_environment;

ALTER TABLE blitzpay.merchant_branches
    DROP COLUMN IF EXISTS stripe_secret_key,
    DROP COLUMN IF EXISTS stripe_publishable_key,
    DROP COLUMN IF EXISTS braintree_merchant_id,
    DROP COLUMN IF EXISTS braintree_public_key,
    DROP COLUMN IF EXISTS braintree_private_key,
    DROP COLUMN IF EXISTS braintree_environment;
-- rollback ALTER TABLE blitzpay.merchant_branches
-- rollback     ADD COLUMN braintree_environment VARCHAR(64),
-- rollback     ADD COLUMN braintree_private_key VARCHAR(512),
-- rollback     ADD COLUMN braintree_public_key VARCHAR(255),
-- rollback     ADD COLUMN braintree_merchant_id VARCHAR(255),
-- rollback     ADD COLUMN stripe_publishable_key VARCHAR(512),
-- rollback     ADD COLUMN stripe_secret_key VARCHAR(512);
-- rollback ALTER TABLE blitzpay.merchant_applications
-- rollback     ADD COLUMN braintree_environment VARCHAR(64),
-- rollback     ADD COLUMN braintree_private_key VARCHAR(512),
-- rollback     ADD COLUMN braintree_public_key VARCHAR(255),
-- rollback     ADD COLUMN braintree_merchant_id VARCHAR(255),
-- rollback     ADD COLUMN stripe_publishable_key VARCHAR(512),
-- rollback     ADD COLUMN stripe_secret_key VARCHAR(512);
