-- liquibase formatted sql

-- changeset mehdi:20260519-001-backfill-geofence-radius
-- Rows created before the geofence radius was enforced may have lat/lng set
-- but geofence_radius_m = NULL. The JPA entity maps this to a non-nullable
-- Int, causing a mapping exception on load.
-- Value matches MerchantLocation.DEFAULT_GEOFENCE_RADIUS_METERS = 500.
UPDATE blitzpay.merchant_branches
SET geofence_radius_m = 500
WHERE latitude IS NOT NULL
  AND geofence_radius_m IS NULL;

UPDATE blitzpay.merchant_applications
SET geofence_radius_m = 500
WHERE latitude IS NOT NULL
  AND geofence_radius_m IS NULL;

-- Set column default so future inserts without an explicit radius are safe at the DB layer.
ALTER TABLE blitzpay.merchant_branches     ALTER COLUMN geofence_radius_m SET DEFAULT 500;
ALTER TABLE blitzpay.merchant_applications ALTER COLUMN geofence_radius_m SET DEFAULT 500;
-- rollback ALTER TABLE blitzpay.merchant_branches     ALTER COLUMN geofence_radius_m DROP DEFAULT;
-- rollback ALTER TABLE blitzpay.merchant_applications ALTER COLUMN geofence_radius_m DROP DEFAULT;
-- rollback UPDATE blitzpay.merchant_branches     SET geofence_radius_m = NULL WHERE geofence_radius_m = 500 AND latitude IS NOT NULL;
-- rollback UPDATE blitzpay.merchant_applications SET geofence_radius_m = NULL WHERE geofence_radius_m = 500 AND latitude IS NOT NULL;
