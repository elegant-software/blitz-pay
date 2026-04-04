package com.elegant.software.blitzpay.mobileobservability.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.modulith.NamedInterface

/**
 * Public API for the mobile-observability module.
 *
 * Accepts batched log events from mobile clients and forwards them
 * to the configured OTLP-compatible endpoint (e.g. Grafana Loki).
 */
@NamedInterface("MobileObservabilityGateway")
interface MobileLogsForwarder {
    fun forward(request: MobileLogsRequest): Int
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class MobileLogsRequest(
    @field:Valid
    val context: MobileContext? = null,
    @field:Valid
    @field:NotEmpty
    val events: List<MobileEvent>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MobileContext(
    val serviceName: String? = null,
    val serviceVersion: String? = null,
    val environment: String? = null,
    val sessionId: String? = null,
    val osName: String? = null,
    val osVersion: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MobileEvent(
    val timestamp: String? = null,
    val severityText: String? = null,
    @field:NotBlank
    val message: String,
    val attributes: Map<String, Any>? = null,
    @field:Valid
    val exception: MobileException? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MobileException(
    val type: String? = null,
    val message: String? = null,
    val stack: String? = null,
    val isFatal: Boolean? = null
)

data class AcceptedResponse(val accepted: Int)
