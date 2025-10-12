package com.elegant.software.quickpay.payments.support

import com.elegant.software.quickpay.payments.truelayer.api.PaymentResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.time.Duration
import java.util.UUID

class PaymentUpdateBusTest {

    @Test
    fun `sink should create new sink for payment request id`() {
        // Given
        val bus = PaymentUpdateBus()
        val paymentRequestId = UUID.randomUUID()

        // When
        val sink = bus.sink(paymentRequestId)

        // Then
        assertThat(sink).isNotNull()
        assertThat(sink).isInstanceOf(Sinks.Many::class.java)
    }

    @Test
    fun `sink should return same sink for same payment request id`() {
        // Given
        val bus = PaymentUpdateBus()
        val paymentRequestId = UUID.randomUUID()

        // When
        val sink1 = bus.sink(paymentRequestId)
        val sink2 = bus.sink(paymentRequestId)

        // Then
        assertThat(sink1).isSameAs(sink2)
    }

    @Test
    fun `sink should return different sinks for different payment request ids`() {
        // Given
        val bus = PaymentUpdateBus()
        val paymentRequestId1 = UUID.randomUUID()
        val paymentRequestId2 = UUID.randomUUID()

        // When
        val sink1 = bus.sink(paymentRequestId1)
        val sink2 = bus.sink(paymentRequestId2)

        // Then
        assertThat(sink1).isNotSameAs(sink2)
    }

    @Test
    fun `emit should send payment result to sink`() {
        // Given
        val bus = PaymentUpdateBus()
        val paymentRequestId = UUID.randomUUID()
        val paymentResult = PaymentResult(
            paymentRequestId = paymentRequestId,
            orderId = "order-123",
            paymentId = "payment-456"
        )

        val flux = bus.sink(paymentRequestId).asFlux()

        // When
        bus.emit(paymentRequestId, paymentResult)

        // Then
        StepVerifier.create(flux.take(1))
            .expectNext(paymentResult)
            .expectComplete()
            .verify(Duration.ofSeconds(2))
    }

    @Test
    fun `emit should handle multiple payment results`() {
        // Given
        val bus = PaymentUpdateBus()
        val paymentRequestId = UUID.randomUUID()
        val result1 = PaymentResult(
            paymentRequestId = paymentRequestId,
            orderId = "order-1"
        )
        val result2 = PaymentResult(
            paymentRequestId = paymentRequestId,
            orderId = "order-2",
            paymentId = "payment-123"
        )
        val result3 = PaymentResult(
            paymentRequestId = paymentRequestId,
            orderId = "order-3",
            paymentId = "payment-456"
        )

        val flux = bus.sink(paymentRequestId).asFlux()

        // When
        bus.emit(paymentRequestId, result1)
        bus.emit(paymentRequestId, result2)
        bus.emit(paymentRequestId, result3)

        // Then
        StepVerifier.create(flux.take(3))
            .expectNext(result1)
            .expectNext(result2)
            .expectNext(result3)
            .expectComplete()
            .verify(Duration.ofSeconds(2))
    }

    @Test
    fun `complete should remove sink and complete flux`() {
        // Given
        val bus = PaymentUpdateBus()
        val paymentRequestId = UUID.randomUUID()
        val paymentResult = PaymentResult(
            paymentRequestId = paymentRequestId,
            orderId = "order-complete"
        )

        val flux = bus.sink(paymentRequestId).asFlux()

        // When
        bus.emit(paymentRequestId, paymentResult)
        bus.complete(paymentRequestId)

        // Then
        StepVerifier.create(flux)
            .expectNext(paymentResult)
            .expectComplete()
            .verify(Duration.ofSeconds(2))
    }

    @Test
    fun `complete should handle non-existent payment request id gracefully`() {
        // Given
        val bus = PaymentUpdateBus()
        val paymentRequestId = UUID.randomUUID()

        // When & Then - should not throw exception
        bus.complete(paymentRequestId)
    }

    @Test
    fun `emit should work for multiple concurrent payment requests`() {
        // Given
        val bus = PaymentUpdateBus()
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

        val flux1 = bus.sink(paymentRequestId1).asFlux()
        val flux2 = bus.sink(paymentRequestId2).asFlux()

        // When
        bus.emit(paymentRequestId1, result1)
        bus.emit(paymentRequestId2, result2)

        // Then
        StepVerifier.create(flux1.take(1))
            .expectNext(result1)
            .expectComplete()
            .verify(Duration.ofSeconds(2))

        StepVerifier.create(flux2.take(1))
            .expectNext(result2)
            .expectComplete()
            .verify(Duration.ofSeconds(2))
    }

    @Test
    fun `new sink should be created after complete`() {
        // Given
        val bus = PaymentUpdateBus()
        val paymentRequestId = UUID.randomUUID()

        // When
        val sink1 = bus.sink(paymentRequestId)
        bus.complete(paymentRequestId)
        val sink2 = bus.sink(paymentRequestId)

        // Then
        assertThat(sink1).isNotSameAs(sink2)
    }
}
