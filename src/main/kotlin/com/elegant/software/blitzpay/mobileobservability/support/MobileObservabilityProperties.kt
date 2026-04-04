package com.elegant.software.blitzpay.mobileobservability.support

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "observability.otlp")
data class MobileObservabilityProperties(
    @field:NotBlank
    val logsEndpoint: String,
    val authHeader: String? = null,
    val authValue: String? = null,
    val serviceNamespace: String = "blitzpay",
    @field:Min(1) @field:Max(1000)
    val maxEventsPerRequest: Int = 100
)
