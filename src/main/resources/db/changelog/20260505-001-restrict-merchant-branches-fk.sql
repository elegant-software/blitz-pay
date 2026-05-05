-- liquibase formatted sql

-- changeset mehdi:20260505-001-restrict-merchant-branches-fk
-- Remediate: fk_merchant_branches_application was ON DELETE CASCADE (added in 20260424-002-branch-merchant-fk-cascade.sql).
-- Recreate as RESTRICT to enforce explicit service-layer deletion per liquibase-best-practices.md Section 10.
ALTER TABLE blitzpay.merchant_branches
    DROP CONSTRAINT fk_merchant_branches_application,
    ADD CONSTRAINT fk_merchant_branches_application
        FOREIGN KEY (merchant_application_id)
        REFERENCES blitzpay.merchant_applications (id)
        ON DELETE RESTRICT;
-- rollback ALTER TABLE blitzpay.merchant_branches DROP CONSTRAINT fk_merchant_branches_application, ADD CONSTRAINT fk_merchant_branches_application FOREIGN KEY (merchant_application_id) REFERENCES blitzpay.merchant_applications (id) ON DELETE CASCADE;