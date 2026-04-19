-- liquibase formatted sql

-- changeset mehdi:20260418-002-merchant-monitoring-records
CREATE TABLE blitzpay.merchant_monitoring_records (
    id                  UUID        NOT NULL,
    status              VARCHAR(32) NOT NULL,
    last_trigger_reason TEXT        NOT NULL,
    last_reviewed_at    TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_merchant_monitoring_records PRIMARY KEY (id)
);
-- rollback DROP TABLE blitzpay.merchant_monitoring_records;

-- changeset mehdi:20260418-003-merchant-applications
CREATE TABLE blitzpay.merchant_applications (
    id                      UUID         NOT NULL,
    application_reference   VARCHAR(64)  NOT NULL,
    legal_business_name     VARCHAR(255) NOT NULL,
    business_type           VARCHAR(64)  NOT NULL,
    registration_number     VARCHAR(64)  NOT NULL,
    operating_country       VARCHAR(8)   NOT NULL,
    primary_business_address TEXT        NOT NULL,
    full_name               VARCHAR(255) NOT NULL,
    email                   VARCHAR(255) NOT NULL,
    phone_number            VARCHAR(64)  NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    submitted_at            TIMESTAMPTZ,
    last_updated_at         TIMESTAMPTZ  NOT NULL,
    risk_level              VARCHAR(32),
    risk_score              INTEGER,
    risk_rationale          TEXT,
    risk_assessed_at        TIMESTAMPTZ,
    monitoring_record_id    UUID,
    CONSTRAINT pk_merchant_applications PRIMARY KEY (id),
    CONSTRAINT uk_merchant_applications_ref UNIQUE (application_reference),
    CONSTRAINT fk_merchant_applications_monitoring FOREIGN KEY (monitoring_record_id) REFERENCES blitzpay.merchant_monitoring_records (id)
);
CREATE INDEX ix_merchant_applications_registration ON blitzpay.merchant_applications (registration_number);
-- rollback DROP TABLE blitzpay.merchant_applications;

-- changeset mehdi:20260418-004-merchant-people
CREATE TABLE blitzpay.merchant_people (
    merchant_application_id UUID         NOT NULL,
    full_name               VARCHAR(255) NOT NULL,
    role                    VARCHAR(64)  NOT NULL,
    country_of_residence    VARCHAR(8)   NOT NULL,
    ownership_percentage    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT fk_merchant_people_application FOREIGN KEY (merchant_application_id) REFERENCES blitzpay.merchant_applications (id)
);
CREATE INDEX ix_merchant_people_application ON blitzpay.merchant_people (merchant_application_id);
-- rollback DROP TABLE blitzpay.merchant_people;

-- changeset mehdi:20260418-005-merchant-supporting-materials
CREATE TABLE blitzpay.merchant_supporting_materials (
    merchant_application_id UUID         NOT NULL,
    type                    VARCHAR(64)  NOT NULL,
    file_name               VARCHAR(255) NOT NULL,
    storage_key             VARCHAR(255) NOT NULL,
    uploaded_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_merchant_materials_application FOREIGN KEY (merchant_application_id) REFERENCES blitzpay.merchant_applications (id)
);
CREATE INDEX ix_merchant_materials_application ON blitzpay.merchant_supporting_materials (merchant_application_id);
-- rollback DROP TABLE blitzpay.merchant_supporting_materials;

-- changeset mehdi:20260418-006-merchant-review-decisions
CREATE TABLE blitzpay.merchant_review_decisions (
    merchant_application_id UUID         NOT NULL,
    outcome                 VARCHAR(32)  NOT NULL,
    reason                  TEXT         NOT NULL,
    reviewer_id             VARCHAR(64)  NOT NULL,
    decided_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_merchant_decisions_application FOREIGN KEY (merchant_application_id) REFERENCES blitzpay.merchant_applications (id)
);
CREATE INDEX ix_merchant_decisions_application ON blitzpay.merchant_review_decisions (merchant_application_id);
-- rollback DROP TABLE blitzpay.merchant_review_decisions;

-- changeset mehdi:20260419-007-merchant-logo-storage-key
ALTER TABLE blitzpay.merchant_applications
    ADD COLUMN logo_storage_key VARCHAR(255);
-- rollback ALTER TABLE blitzpay.merchant_applications DROP COLUMN logo_storage_key;
