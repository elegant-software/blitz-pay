-- liquibase formatted sql

-- changeset mehdi:20260419-008-merchant-products
CREATE TABLE blitzpay.merchant_products (
    id                      UUID           NOT NULL,
    merchant_application_id UUID           NOT NULL,
    name                    VARCHAR(255)   NOT NULL,
    unit_price              DECIMAL(12, 4) NOT NULL,
    image_url               VARCHAR(2048),
    active                  BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_merchant_products PRIMARY KEY (id),
    CONSTRAINT chk_merchant_products_price CHECK (unit_price >= 0),
    CONSTRAINT fk_merchant_products_application
        FOREIGN KEY (merchant_application_id)
        REFERENCES blitzpay.merchant_applications (id)
);
CREATE INDEX ix_merchant_products_merchant ON blitzpay.merchant_products (merchant_application_id);
CREATE INDEX ix_merchant_products_active   ON blitzpay.merchant_products (merchant_application_id, active);
-- rollback DROP TABLE blitzpay.merchant_products;

-- changeset mehdi:20260419-009-merchant-products-rls
ALTER TABLE blitzpay.merchant_products ENABLE ROW LEVEL SECURITY;
ALTER TABLE blitzpay.merchant_products FORCE ROW LEVEL SECURITY;
CREATE POLICY merchant_tenant_isolation
    ON blitzpay.merchant_products
    USING (
        merchant_application_id = NULLIF(current_setting('app.current_merchant_id', true), '')::uuid
    );
-- rollback DROP POLICY merchant_tenant_isolation ON blitzpay.merchant_products;
--         ALTER TABLE blitzpay.merchant_products DISABLE ROW LEVEL SECURITY;
