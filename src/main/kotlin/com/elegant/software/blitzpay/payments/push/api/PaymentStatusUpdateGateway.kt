package com.elegant.software.blitzpay.payments.push.api

import java.time.Instant
import java.util.UUID

interface PaymentStatusUpdateGateway {
    fun settle(paymentRequestId: UUID, sourceEventId: String, occurredAt: Instant = Instant.now())
    fun fail(paymentRequestId: UUID, sourceEventId: String, occurredAt: Instant = Instant.now())
}