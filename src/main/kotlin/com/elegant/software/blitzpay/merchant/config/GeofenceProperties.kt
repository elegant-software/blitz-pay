package com.elegant.software.blitzpay.merchant.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "blitzpay.geofence")
data class GeofenceProperties(
    val proximityCooldownSeconds: Long = 30
)
