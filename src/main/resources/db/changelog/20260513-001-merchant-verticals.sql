-- liquibase formatted sql

-- changeset mehdi:20260513-001-create-merchant-verticals
CREATE TABLE blitzpay.merchant_verticals (
    code         VARCHAR(64)  NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_merchant_verticals PRIMARY KEY (code)
);
-- rollback DROP TABLE blitzpay.merchant_verticals;

-- changeset mehdi:20260513-002-seed-merchant-verticals
INSERT INTO blitzpay.merchant_verticals (code, display_name) VALUES
    ('RESTAURANT',    'Restaurant'),
    ('CAFE',          'Café'),
    ('BAR',           'Bar & Pub'),
    ('BARBER_SHOP',   'Barber Shop'),
    ('BEAUTY_SALON',  'Beauty Salon'),
    ('RETAIL',        'Retail Store'),
    ('GYM',           'Gym & Fitness'),
    ('PHARMACY',      'Pharmacy'),
    ('ICE_CREAM_SHOP','Ice Cream Shop');
-- rollback DELETE FROM blitzpay.merchant_verticals WHERE code IN ('RESTAURANT','CAFE','BAR','BARBER_SHOP','BEAUTY_SALON','RETAIL','GYM','PHARMACY','ICE_CREAM_SHOP');

-- changeset mehdi:20260513-003-migrate-merchant-business-type
UPDATE blitzpay.merchant_applications
SET business_type = 'RETAIL'
WHERE business_type NOT IN (SELECT code FROM blitzpay.merchant_verticals);
-- rollback SELECT 1; -- data migration, no safe rollback

-- changeset mehdi:20260513-004-fk-merchant-applications-vertical
ALTER TABLE blitzpay.merchant_applications
    ADD CONSTRAINT fk_merchant_applications_vertical
        FOREIGN KEY (business_type) REFERENCES blitzpay.merchant_verticals (code);
-- rollback ALTER TABLE blitzpay.merchant_applications DROP CONSTRAINT fk_merchant_applications_vertical;
