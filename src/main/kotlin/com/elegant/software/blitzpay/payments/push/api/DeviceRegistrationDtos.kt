package com.elegant.software.blitzpay.payments.push.api

import com.elegant.software.blitzpay.payments.push.persistence.DevicePlatform
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class DeviceRegistrationRequest(
    @field:NotNull
    val paymentRequestId: UUID?,

    @field:NotNull
    @field:Pattern(regexp = "^ExponentPushToken\\[[^\\]]+\\]$")
    val expoPushToken: String?,

    val platform: DevicePlatform? = null,
)

data class DeviceRegistrationResponse(
    val id: UUID,
    val paymentRequestId: UUID?,
    val expoPushToken: String,
    val platform: DevicePlatform?,
)