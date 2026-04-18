package com.elegant.software.blitzpay.payments.braintree

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.payments.braintree.internal.BraintreeCheckoutResult
import com.elegant.software.blitzpay.payments.braintree.internal.BraintreePaymentService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Mono

class BraintreePaymentControllerContractTest : ContractVerifierBase() {

    @MockitoBean
    private lateinit var braintreePaymentService: BraintreePaymentService

    @Test
    fun `client-token returns 200 with token when configured`() {
        whenever(braintreePaymentService.isConfigured()).thenReturn(true)
        whenever(braintreePaymentService.generateClientToken()).thenReturn(
            Mono.just("eyJ2ZXJzaW9uIjoy_contract_test_token")
        )

        webTestClient.post().uri("/v1/payments/braintree/client-token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.clientToken").isEqualTo("eyJ2ZXJzaW9uIjoy_contract_test_token")
    }

    @Test
    fun `client-token returns 503 when not configured`() {
        whenever(braintreePaymentService.isConfigured()).thenReturn(false)

        webTestClient.post().uri("/v1/payments/braintree/client-token")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Braintree not configured on server")
    }

    @Test
    fun `checkout returns success response for valid nonce and amount`() {
        whenever(braintreePaymentService.isConfigured()).thenReturn(true)
        whenever(braintreePaymentService.checkout(any(), any(), any(), anyOrNull())).thenReturn(
            Mono.just(BraintreeCheckoutResult.Success("tx_abc123", "12.50", "EUR", null))
        )

        webTestClient.post().uri("/v1/payments/braintree/checkout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"nonce": "fake-valid-nonce", "amount": 12.50, "currency": "EUR"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("succeeded")
            .jsonPath("$.transactionId").isEqualTo("tx_abc123")
            .jsonPath("$.amount").isEqualTo("12.50")
    }

    @Test
    fun `checkout echoes invoiceId in success response`() {
        whenever(braintreePaymentService.isConfigured()).thenReturn(true)
        whenever(braintreePaymentService.checkout(any(), any(), any(), anyOrNull())).thenReturn(
            Mono.just(BraintreeCheckoutResult.Success("tx_inv42", "99.00", "EUR", "INV-2026-00042"))
        )

        webTestClient.post().uri("/v1/payments/braintree/checkout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"nonce": "fake-valid-nonce", "amount": 99.00, "invoiceId": "INV-2026-00042"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("succeeded")
            .jsonPath("$.invoiceId").isEqualTo("INV-2026-00042")
    }

    @Test
    fun `checkout without invoiceId succeeds with no invoiceId in response`() {
        whenever(braintreePaymentService.isConfigured()).thenReturn(true)
        whenever(braintreePaymentService.checkout(any(), any(), any(), anyOrNull())).thenReturn(
            Mono.just(BraintreeCheckoutResult.Success("tx_noinv", "5.00", "EUR", null))
        )

        webTestClient.post().uri("/v1/payments/braintree/checkout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"nonce": "fake-valid-nonce", "amount": 5.00}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("succeeded")
            .jsonPath("$.invoiceId").doesNotExist()
    }

    @Test
    fun `checkout returns 400 when nonce is missing`() {
        whenever(braintreePaymentService.isConfigured()).thenReturn(true)

        webTestClient.post().uri("/v1/payments/braintree/checkout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 12.50}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `checkout returns 503 when not configured`() {
        whenever(braintreePaymentService.isConfigured()).thenReturn(false)

        webTestClient.post().uri("/v1/payments/braintree/checkout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"nonce": "fake-valid-nonce", "amount": 12.50}""")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Braintree not configured on server")
    }
}
