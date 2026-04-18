package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.payments.stripe.internal.StripeIntentResult
import com.elegant.software.blitzpay.payments.stripe.internal.StripePaymentService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Mono

class StripePaymentControllerContractTest : ContractVerifierBase() {

    @MockitoBean
    private lateinit var stripePaymentService: StripePaymentService

    @Test
    fun `returns 200 with paymentIntent and publishableKey for valid amount`() {
        whenever(stripePaymentService.createIntent(any(), any())).thenReturn(
            Mono.just(StripeIntentResult("pi_test_secret_abc", "pk_test_dummy"))
        )

        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 12.50, "currency": "eur"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentIntent").isEqualTo("pi_test_secret_abc")
            .jsonPath("$.publishableKey").isEqualTo("pk_test_dummy")
    }

    @Test
    fun `returns 200 with default currency when currency omitted`() {
        whenever(stripePaymentService.createIntent(any(), any())).thenReturn(
            Mono.just(StripeIntentResult("pi_test_secret_xyz", "pk_test_dummy"))
        )

        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 5.00}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentIntent").exists()
    }

    @Test
    fun `returns 400 when amount is missing`() {
        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"currency": "eur"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `returns 400 when amount is zero`() {
        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 0}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `returns 400 when amount is negative`() {
        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": -1.0}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }
}
