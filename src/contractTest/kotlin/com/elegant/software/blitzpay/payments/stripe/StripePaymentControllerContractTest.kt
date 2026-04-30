package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.merchant.api.StripeCredentials
import com.elegant.software.blitzpay.payments.stripe.internal.StripeIntentResult
import com.elegant.software.blitzpay.payments.stripe.internal.StripePaymentService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Mono
import java.util.UUID

class StripePaymentControllerContractTest : ContractVerifierBase() {

    @MockitoBean
    private lateinit var stripePaymentService: StripePaymentService

    @MockitoBean
    private lateinit var credentialResolver: MerchantCredentialResolver

    private val testMerchantId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val testBranchId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val testCredentials = StripeCredentials(secretKey = "sk_test_dummy", publishableKey = "pk_test_dummy")

    @BeforeEach
    fun setupResolver() {
        whenever(credentialResolver.resolveBranch(any(), anyOrNull(), anyOrNull())).thenReturn(testBranchId)
        whenever(credentialResolver.resolveStripe(any(), anyOrNull())).thenReturn(testCredentials)
        whenever(credentialResolver.resolveProductPrice(any())).thenReturn(null)
    }

    @Test
    fun `returns 200 with clientSecret alias and publishableKey for valid amount`() {
        whenever(stripePaymentService.createIntent(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull())).thenReturn(
            Mono.just(StripeIntentResult("pi_test_secret_abc", "pi_abc123", "pk_test_dummy"))
        )

        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 12.50, "currency": "eur", "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentRequestId").value<String> { paymentRequestId ->
                require(Regex("[0-9a-fA-F\\-]{36}").matches(paymentRequestId))
            }
            .jsonPath("$.clientSecret").isEqualTo("pi_test_secret_abc")
            .jsonPath("$.paymentIntent").isEqualTo("pi_abc123")
            .jsonPath("$.publishableKey").isEqualTo("pk_test_dummy")
    }

    @Test
    fun `returns 200 with default currency when currency omitted`() {
        whenever(stripePaymentService.createIntent(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull())).thenReturn(
            Mono.just(StripeIntentResult("pi_test_secret_xyz", "pi_xyz789", "pk_test_dummy"))
        )

        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 5.00, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentRequestId").value<String> { paymentRequestId ->
                require(Regex("[0-9a-fA-F\\-]{36}").matches(paymentRequestId))
            }
            .jsonPath("$.clientSecret").exists()
            .jsonPath("$.paymentIntent").exists()
    }

    @Test
    fun `returns 400 when merchantId is missing`() {
        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 12.50, "currency": "eur"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `returns 400 when amount is zero`() {
        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 0, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `returns 400 when amount is negative`() {
        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": -1.0, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `returns 503 when stripe credentials not configured`() {
        whenever(credentialResolver.resolveStripe(any(), anyOrNull())).thenReturn(null)

        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 12.50, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.error").exists()
    }
}
