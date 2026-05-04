package com.elegant.software.blitzpay.merchant.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class MerchantLocation(
    @Column(nullable = false)
    val latitude: Double,

    @Column(nullable = false)
    val longitude: Double,

    @Column(name = "geofence_radius_m", nullable = false)
    val geofenceRadiusMeters: Int,

    @Column(name = "google_place_id")
    val googlePlaceId: String? = null,

    @Column(name = "address_line1", insertable = false, updatable = false)
    val addressLine1: String? = null,

    @Column(name = "address_line2", insertable = false, updatable = false)
    val addressLine2: String? = null,

    @Column(name = "city", insertable = false, updatable = false)
    val city: String? = null,

    @Column(name = "postal_code", insertable = false, updatable = false)
    val postalCode: String? = null,

    @Column(name = "country", insertable = false, updatable = false)
    val country: String? = null,

    @Column(name = "place_formatted_address", length = 1024)
    val placeFormattedAddress: String? = null,

    @Column(name = "place_rating")
    val placeRating: Double? = null,

    @Column(name = "place_review_count")
    val placeReviewCount: Int? = null,

    @Column(name = "place_enrichment_status", length = 32)
    val placeEnrichmentStatus: String? = null,

    @Column(name = "place_enrichment_error", length = 1024)
    val placeEnrichmentError: String? = null,

    @Column(name = "place_enriched_at")
    val placeEnrichedAt: java.time.Instant? = null
)
