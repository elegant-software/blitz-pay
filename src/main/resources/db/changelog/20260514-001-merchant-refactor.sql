-- liquibase formatted sql

-- changeset codex:20260514-001-merchant-refactor
ALTER TABLE blitzpay.merchant_applications
    ADD COLUMN merchant_code VARCHAR(64),
    ADD COLUMN merchant_name VARCHAR(255),
    ADD COLUMN merchant_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN website VARCHAR(512),
    ADD COLUMN contact_email_public VARCHAR(255),
    ADD COLUMN contact_phone_public VARCHAR(64);

UPDATE blitzpay.merchant_applications
SET merchant_code = COALESCE(merchant_code, application_reference),
    merchant_name = COALESCE(merchant_name, legal_business_name),
    merchant_status = CASE
        WHEN status IN ('ACTIVE', 'MONITORING') THEN 'ACTIVE'
        ELSE 'INACTIVE'
    END,
    contact_email_public = COALESCE(contact_email_public, email),
    contact_phone_public = COALESCE(contact_phone_public, phone_number)
WHERE merchant_code IS NULL
   OR merchant_name IS NULL
   OR contact_email_public IS NULL
   OR contact_phone_public IS NULL;

ALTER TABLE blitzpay.merchant_applications
    ALTER COLUMN merchant_code SET NOT NULL,
    ALTER COLUMN merchant_name SET NOT NULL;

ALTER TABLE blitzpay.merchant_applications
    ADD CONSTRAINT uk_merchant_applications_merchant_code UNIQUE (merchant_code);

ALTER TABLE blitzpay.merchant_branches
    ADD COLUMN branch_code VARCHAR(64),
    ADD COLUMN website_override VARCHAR(512);

UPDATE blitzpay.merchant_branches
SET branch_code = COALESCE(branch_code, 'BR-' || UPPER(SUBSTRING(REPLACE(id::text, '-', '') FROM 1 FOR 12)))
WHERE branch_code IS NULL;

ALTER TABLE blitzpay.merchant_branches
    ALTER COLUMN branch_code SET NOT NULL;

ALTER TABLE blitzpay.merchant_branches
    ADD CONSTRAINT uk_merchant_branches_code_per_merchant UNIQUE (merchant_application_id, branch_code);

CREATE TABLE blitzpay.merchant_offerings (
    code VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO blitzpay.merchant_offerings (code, display_name, active)
VALUES
    ('PRE_ORDER', 'Pre-Order', TRUE),
    ('WALK_IN_ORDERING', 'Walk-In Ordering', TRUE),
    ('DEFERRED_PAYMENT', 'Deferred Payment', TRUE),
    ('APPOINTMENT_BOOKING', 'Appointment Booking', TRUE)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE blitzpay.merchant_application_offerings (
    merchant_application_id UUID NOT NULL REFERENCES blitzpay.merchant_applications (id),
    offering_code VARCHAR(64) NOT NULL REFERENCES blitzpay.merchant_offerings (code),
    enabled_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_merchant_application_offerings PRIMARY KEY (merchant_application_id, offering_code)
);

ALTER TABLE blitzpay.order_orders
    ADD COLUMN order_type VARCHAR(32) NOT NULL DEFAULT 'PRE_ORDER',
    ADD COLUMN uses_deferred_payment BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE blitzpay.order_orders
SET order_type = CASE
    WHEN creator_type = 'MERCHANT' THEN 'WALK_IN_ORDERING'
    ELSE 'PRE_ORDER'
END;

-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN uses_deferred_payment;
-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN order_type;
-- rollback DROP TABLE blitzpay.merchant_application_offerings;
-- rollback DROP TABLE blitzpay.merchant_offerings;
-- rollback ALTER TABLE blitzpay.merchant_branches DROP CONSTRAINT uk_merchant_branches_code_per_merchant;
-- rollback ALTER TABLE blitzpay.merchant_branches DROP COLUMN website_override;
-- rollback ALTER TABLE blitzpay.merchant_branches DROP COLUMN branch_code;
-- rollback ALTER TABLE blitzpay.merchant_applications DROP CONSTRAINT uk_merchant_applications_merchant_code;
-- rollback ALTER TABLE blitzpay.merchant_applications DROP COLUMN contact_phone_public;
-- rollback ALTER TABLE blitzpay.merchant_applications DROP COLUMN contact_email_public;
-- rollback ALTER TABLE blitzpay.merchant_applications DROP COLUMN website;
-- rollback ALTER TABLE blitzpay.merchant_applications DROP COLUMN merchant_status;
-- rollback ALTER TABLE blitzpay.merchant_applications DROP COLUMN merchant_name;
-- rollback ALTER TABLE blitzpay.merchant_applications DROP COLUMN merchant_code;
