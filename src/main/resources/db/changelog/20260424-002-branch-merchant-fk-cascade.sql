-- liquibase formatted sql

-- changeset mehdi:20260424-002-branch-merchant-fk-cascade
ALTER TABLE blitzpay.merchant_branches
    DROP CONSTRAINT fk_merchant_branches_application,
    ADD CONSTRAINT fk_merchant_branches_application
        FOREIGN KEY (merchant_application_id)
        REFERENCES blitzpay.merchant_applications (id)
        ON DELETE CASCADE;
-- rollback ALTER TABLE blitzpay.merchant_branches DROP CONSTRAINT fk_merchant_branches_application;
-- rollback ALTER TABLE blitzpay.merchant_branches ADD CONSTRAINT fk_merchant_branches_application FOREIGN KEY (merchant_application_id) REFERENCES blitzpay.merchant_applications (id);
