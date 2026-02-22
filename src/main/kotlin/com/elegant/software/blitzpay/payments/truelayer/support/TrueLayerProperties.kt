package com.elegant.software.blitzpay.payments.truelayer.support

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("truelayer")
data class TrueLayerProperties(
    // Legacy fields (kept for backward compatibility)
    val apiBase: String,
    val accessToken: String,

    // Webhook validation
    val webhookJku: String,
    val webhookAudience: String? = null,

    // TrueLayer SDK configuration
    val clientId: String,
    val clientSecret: String,
    val keyId: String,
    val privateKeyPath: String,
    val environment: String = "sandbox", // "sandbox" or "live"
    val httpLogs: Boolean = false,

    // Payments
    val merchantAccountId: String
)

