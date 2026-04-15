package com.elegant.software.blitzpay.payments.push.api

import java.time.Instant
import java.util.UUID

data class PaymentStatusChanged(
    val paymentRequestId: UUID,
    val newStatus: PaymentStatusCode,
    val previousStatus: PaymentStatusCode?,
    val occurredAt: Instant,
    val sourceEventId: String,
)
