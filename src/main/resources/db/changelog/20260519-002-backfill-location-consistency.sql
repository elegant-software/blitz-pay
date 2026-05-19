-- liquibase formatted sql

-- changeset mehdi:20260519-002-location-consistency
-- Rows where geofence_radius_m was set (by the column DEFAULT introduced in
-- 20260519-001) but latitude/longitude are NULL cause a Hibernate mapping
-- error on load: the non-null geofence_radius_m triggers MerchantLocation
-- instantiation, which then fails trying to coerce NULL into latitude: Double.
-- Null out all embeddable-owned location columns for any row without coordinates.
UPDATE blitzpay.merchant_branches
SET geofence_radius_m       = NULL,
    google_place_id          = NULL,
    place_formatted_address  = NULL,
    place_rating             = NULL,
    place_review_count       = NULL,
    place_enrichment_status  = NULL,
    place_enrichment_error   = NULL,
    place_enriched_at        = NULL
WHERE latitude IS NULL;

UPDATE blitzpay.merchant_applications
SET geofence_radius_m       = NULL,
    google_place_id          = NULL,
    place_formatted_address  = NULL,
    place_rating             = NULL,
    place_review_count       = NULL,
    place_enrichment_status  = NULL,
    place_enrichment_error   = NULL,
    place_enriched_at        = NULL
WHERE latitude IS NULL;

-- Enforce lat/lng symmetry: both present or both absent.
ALTER TABLE blitzpay.merchant_branches
    ADD CONSTRAINT chk_branch_lat_lng_both_or_neither
        CHECK ((latitude IS NULL) = (longitude IS NULL));

ALTER TABLE blitzpay.merchant_applications
    ADD CONSTRAINT chk_merchant_lat_lng_both_or_neither
        CHECK ((latitude IS NULL) = (longitude IS NULL));

-- Enforce that geofence_radius_m is only set when coordinates are present.
-- This prevents the DEFAULT 500 (from 20260519-001) from creating broken rows
-- via raw SQL inserts that supply coordinates but omit the radius.
ALTER TABLE blitzpay.merchant_branches
    ADD CONSTRAINT chk_branch_geofence_requires_location
        CHECK (geofence_radius_m IS NULL OR latitude IS NOT NULL);

ALTER TABLE blitzpay.merchant_applications
    ADD CONSTRAINT chk_merchant_geofence_requires_location
        CHECK (geofence_radius_m IS NULL OR latitude IS NOT NULL);

-- rollback ALTER TABLE blitzpay.merchant_applications DROP CONSTRAINT chk_merchant_geofence_requires_location;
-- rollback ALTER TABLE blitzpay.merchant_branches DROP CONSTRAINT chk_branch_geofence_requires_location;
-- rollback ALTER TABLE blitzpay.merchant_applications DROP CONSTRAINT chk_merchant_lat_lng_both_or_neither;
-- rollback ALTER TABLE blitzpay.merchant_branches DROP CONSTRAINT chk_branch_lat_lng_both_or_neither;