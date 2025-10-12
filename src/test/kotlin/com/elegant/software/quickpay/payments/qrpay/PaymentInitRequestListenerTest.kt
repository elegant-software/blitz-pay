package com.elegant.software.quickpay.payments.qrpay

import TlWebhookEnvelope
import com.elegant.software.quickpay.payments.support.PaymentUpdateBus
import com.elegant.software.quickpay.payments.truelayer.api.PaymentResult
import com.elegant.software.quickpay.payments.truelayer.outbound.PaymentService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.mock
import java.util.UUID

class PaymentInitRequestListenerTest {

    private val gateway: PaymentService = mock()
    private val paymentUpdateBus: PaymentUpdateBus = mock()
    private val listener = PaymentInitRequestListener(gateway, paymentUpdateBus)

    @Test
    fun `on PaymentResult should emit to payment update bus`() {
        // Given
        val paymentRequestId = UUID.randomUUID()
        val paymentResult = PaymentResult(
            paymentRequestId = paymentRequestId,
            orderId = "order-123",
            paymentId = "payment-456"
        )

        // When
        listener.on(paymentResult)

        // Then
        val uuidCaptor = argumentCaptor<UUID>()
        val resultCaptor = argumentCaptor<PaymentResult>()
        verify(paymentUpdateBus).emit(uuidCaptor.capture(), resultCaptor.capture())

        assertThat(uuidCaptor.firstValue).isEqualTo(paymentRequestId)
        assertThat(resultCaptor.firstValue).isEqualTo(paymentResult)
    }

    @Test
    fun `on PaymentResult should handle multiple results`() {
        // Given
        val paymentRequestId1 = UUID.randomUUID()
        val paymentRequestId2 = UUID.randomUUID()
        
        val result1 = PaymentResult(
            paymentRequestId = paymentRequestId1,
            orderId = "order-1"
        )
        val result2 = PaymentResult(
            paymentRequestId = paymentRequestId2,
            orderId = "order-2"
        )

        // When
        listener.on(result1)
        listener.on(result2)

        // Then
        verify(paymentUpdateBus).emit(paymentRequestId1, result1)
        verify(paymentUpdateBus).emit(paymentRequestId2, result2)
    }

    @Test
    fun `on TlWebhookEnvelope should complete payment when metadata contains paymentRequestId`() {
        // Given
        val paymentRequestId = UUID.randomUUID()
        val webhook = TlWebhookEnvelope(
            type = "payment_executed",
            event_id = "event-123",
            payment_id = "payment-456",
            metadata = mapOf("paymentRequestId" to paymentRequestId.toString())
        )

        // When
        listener.on(webhook)

        // Then
        verify(paymentUpdateBus).complete(paymentRequestId)
    }

    @Test
    fun `on TlWebhookEnvelope should handle webhook without metadata gracefully`() {
        // Given
        val webhook = TlWebhookEnvelope(
            type = "payment_executed",
            event_id = "event-123",
            payment_id = "payment-456",
            metadata = null
        )

        // When - should not throw exception
        listener.on(webhook)

        // Then - no interaction with paymentUpdateBus
        verify(paymentUpdateBus, org.mockito.kotlin.never()).complete(org.mockito.kotlin.any())
    }

    @Test
    fun `on TlWebhookEnvelope should handle webhook with empty metadata gracefully`() {
        // Given
        val webhook = TlWebhookEnvelope(
            type = "payment_executed",
            event_id = "event-123",
            payment_id = "payment-456",
            metadata = emptyMap()
        )

        // When
        listener.on(webhook)

        // Then - no interaction with paymentUpdateBus
        verify(paymentUpdateBus, org.mockito.kotlin.never()).complete(org.mockito.kotlin.any())
    }

    @Test
    fun `on TlWebhookEnvelope should handle webhook with invalid paymentRequestId`() {
        // Given
        val webhook = TlWebhookEnvelope(
            type = "payment_executed",
            event_id = "event-123",
            payment_id = "payment-456",
            metadata = mapOf("paymentRequestId" to "not-a-uuid")
        )

        // When & Then - should throw exception for invalid UUID
        try {
            listener.on(webhook)
            assertThat(false).isTrue() // Should not reach here
        } catch (e: IllegalArgumentException) {
            // Expected exception
            assertThat(e).isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `on TlWebhookEnvelope should handle multiple webhooks`() {
        // Given
        val paymentRequestId1 = UUID.randomUUID()
        val paymentRequestId2 = UUID.randomUUID()
        
        val webhook1 = TlWebhookEnvelope(
            type = "payment_executed",
            event_id = "event-1",
            payment_id = "payment-1",
            metadata = mapOf("paymentRequestId" to paymentRequestId1.toString())
        )
        val webhook2 = TlWebhookEnvelope(
            type = "payment_executed",
            event_id = "event-2",
            payment_id = "payment-2",
            metadata = mapOf("paymentRequestId" to paymentRequestId2.toString())
        )

        // When
        listener.on(webhook1)
        listener.on(webhook2)

        // Then
        verify(paymentUpdateBus).complete(paymentRequestId1)
        verify(paymentUpdateBus).complete(paymentRequestId2)
    }

    @Test
    fun `on TlWebhookEnvelope should handle different event types`() {
        // Given
        val eventTypes = listOf("payment_executed", "payment_settled", "payment_failed")

        eventTypes.forEach { eventType ->
            val paymentRequestId = UUID.randomUUID() // Use different ID for each event
            val webhook = TlWebhookEnvelope(
                type = eventType,
                event_id = "event-$eventType",
                payment_id = "payment-123",
                metadata = mapOf("paymentRequestId" to paymentRequestId.toString())
            )

            // When
            listener.on(webhook)

            // Then
            verify(paymentUpdateBus).complete(paymentRequestId)
        }
    }
}
