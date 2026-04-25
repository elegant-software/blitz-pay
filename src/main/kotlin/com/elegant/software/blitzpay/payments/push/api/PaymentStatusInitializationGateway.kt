package com.elegant.software.blitzpay.payments.push.api

import java.util.UUID

interface PaymentStatusInitializationGateway {
    fun initialize(
        paymentRequestId: UUID,
        payerRef: String?,
        orderId: String?,
        amountMinorUnits: Long?,
        currency: String?,
    )
}
