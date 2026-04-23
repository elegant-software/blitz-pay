-- liquibase formatted sql

-- changeset mehdi:20260423-002-active-payment-channel
CREATE TABLE IF NOT EXISTS blitzpay.merchant_application_payment_channels (
    merchant_application_id UUID NOT NULL,
    payment_channel         VARCHAR(32) NOT NULL,
    CONSTRAINT fk_merchant_application_payment_channels_application
        FOREIGN KEY (merchant_application_id) REFERENCES blitzpay.merchant_applications (id),
    CONSTRAINT uq_merchant_application_payment_channels
        UNIQUE (merchant_application_id, payment_channel)
);

CREATE TABLE IF NOT EXISTS blitzpay.merchant_branch_payment_channels (
    merchant_branch_id UUID NOT NULL,
    payment_channel    VARCHAR(32) NOT NULL,
    CONSTRAINT fk_merchant_branch_payment_channels_branch
        FOREIGN KEY (merchant_branch_id) REFERENCES blitzpay.merchant_branches (id),
    CONSTRAINT uq_merchant_branch_payment_channels
        UNIQUE (merchant_branch_id, payment_channel)
);
-- rollback DROP TABLE IF EXISTS blitzpay.merchant_branch_payment_channels;
-- rollback DROP TABLE IF EXISTS blitzpay.merchant_application_payment_channels;
