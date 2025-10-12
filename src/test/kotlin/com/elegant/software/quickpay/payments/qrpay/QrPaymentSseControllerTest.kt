package com.elegant.software.quickpay.payments.qrpay

import com.elegant.software.quickpay.payments.support.PaymentUpdateBus
import com.elegant.software.quickpay.payments.truelayer.api.PaymentResult
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Sinks
import java.util.UUID

@WebFluxTest(QrPaymentSseController::class)
class QrPaymentSseControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var paymentUpdateBus: PaymentUpdateBus

    @Test
    fun `stream endpoint should exist and accept GET requests`() {
        // Given
        val paymentRequestId = UUID.randomUUID()
        val mockSink = Sinks.many().multicast().onBackpressureBuffer<PaymentResult>()
        
        whenever(paymentUpdateBus.sink(paymentRequestId)).thenReturn(mockSink)
        
        // Complete the sink immediately so the test doesn't hang
        mockSink.tryEmitComplete()

        // When & Then - verify endpoint exists and returns proper content type
        webTestClient.get()
            .uri("/qr-payments/$paymentRequestId/events")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
    }

    @Test
    fun `stream should handle valid UUID path parameter`() {
        // Given
        val paymentRequestId = UUID.randomUUID()
        val mockSink = Sinks.many().multicast().onBackpressureBuffer<PaymentResult>()
        
        whenever(paymentUpdateBus.sink(paymentRequestId)).thenReturn(mockSink)
        mockSink.tryEmitComplete()

        // When & Then
        webTestClient.get()
            .uri("/qr-payments/$paymentRequestId/events")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `stream should call paymentUpdateBus with correct payment request id`() {
        // Given
        val paymentRequestId = UUID.randomUUID()
        val mockSink = Sinks.many().multicast().onBackpressureBuffer<PaymentResult>()
        
        whenever(paymentUpdateBus.sink(paymentRequestId)).thenReturn(mockSink)
        mockSink.tryEmitComplete()

        // When
        webTestClient.get()
            .uri("/qr-payments/$paymentRequestId/events")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk

        // Then - verify the sink was requested with correct ID
        org.mockito.kotlin.verify(paymentUpdateBus).sink(paymentRequestId)
    }

    @Test
    fun `stream should work with different payment request ids`() {
        // Given
        val paymentRequestId1 = UUID.randomUUID()
        val paymentRequestId2 = UUID.randomUUID()
        val mockSink1 = Sinks.many().multicast().onBackpressureBuffer<PaymentResult>()
        val mockSink2 = Sinks.many().multicast().onBackpressureBuffer<PaymentResult>()
        
        whenever(paymentUpdateBus.sink(paymentRequestId1)).thenReturn(mockSink1)
        whenever(paymentUpdateBus.sink(paymentRequestId2)).thenReturn(mockSink2)
        
        mockSink1.tryEmitComplete()
        mockSink2.tryEmitComplete()

        // When & Then - verify both endpoints work independently
        webTestClient.get()
            .uri("/qr-payments/$paymentRequestId1/events")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk

        webTestClient.get()
            .uri("/qr-payments/$paymentRequestId2/events")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
    }
}
