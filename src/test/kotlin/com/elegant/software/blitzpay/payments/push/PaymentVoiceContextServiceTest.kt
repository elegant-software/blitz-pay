package com.elegant.software.blitzpay.payments.push

import com.elegant.software.blitzpay.payments.push.api.PaymentStatusCode
import com.elegant.software.blitzpay.payments.push.api.RecentPaymentSummary
import com.elegant.software.blitzpay.payments.push.internal.PaymentStatusService
import com.elegant.software.blitzpay.payments.push.internal.PaymentVoiceContextService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class PaymentVoiceContextServiceTest {
    private val paymentStatusService = mock<PaymentStatusService>()
    private val service = PaymentVoiceContextService(paymentStatusService)

    @Test
    fun `delegates recent payment lookup to payment status service`() {
        val expected = listOf(
            RecentPaymentSummary(
                paymentRequestId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                status = PaymentStatusCode.SETTLED,
                updatedAt = Instant.parse("2026-04-25T10:15:30Z"),
                orderId = "ORD-42",
                amountMinorUnits = 1299,
                currency = "EUR",
            )
        )
        whenever(paymentStatusService.findRecentBySubject("user-123", 3)).thenReturn(expected)

        val result = service.findRecentPaymentsBySubject("user-123", 3)

        assertEquals(expected, result)
    }
}
