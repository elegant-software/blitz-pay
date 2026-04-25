package com.elegant.software.blitzpay.payments.push.internal

import com.elegant.software.blitzpay.payments.push.api.PaymentVoiceContextGateway
import com.elegant.software.blitzpay.payments.push.api.RecentPaymentSummary
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PaymentVoiceContextService(
    private val paymentStatusService: PaymentStatusService,
) : PaymentVoiceContextGateway, com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway {

    override fun findRecentPaymentsBySubject(subject: String, limit: Int): List<RecentPaymentSummary> =
        paymentStatusService.findRecentBySubject(subject, limit)

    override fun initialize(
        paymentRequestId: UUID,
        payerRef: String?,
        orderId: String?,
        amountMinorUnits: Long?,
        currency: String?,
    ) {
        paymentStatusService.initialize(
            paymentRequestId = paymentRequestId,
            payerRef = payerRef,
            orderId = orderId,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
        )
    }
}
