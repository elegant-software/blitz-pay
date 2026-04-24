package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

data class GeofenceRegionResponse(
    val regionId: String,
    val regionType: String,
    val sourceId: UUID,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val distanceMeters: Double? = null,
)

data class GeofenceRegionsResponse(
    val regions: List<GeofenceRegionResponse>,
)
