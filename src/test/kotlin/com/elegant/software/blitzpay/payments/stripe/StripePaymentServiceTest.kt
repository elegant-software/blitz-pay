package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.merchant.api.StripeCredentials
import com.elegant.software.blitzpay.payments.stripe.internal.StripePaymentService
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.UUID

class StripePaymentServiceTest {

    private val service = StripePaymentService()
    private val credentials = StripeCredentials(secretKey = "sk_test_dummy", publishableKey = "pk_test_dummy")
    private val merchantId: UUID = UUID.randomUUID()
    private val paymentRequestId: UUID = UUID.randomUUID()

    @Test
    fun `rejects zero amount`() {
        StepVerifier.create(service.createIntent(0.0, "eur", credentials, merchantId, null, "ORDER-1", paymentRequestId, null))
            .expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("positive") }
            .verify()
    }

    @Test
    fun `rejects negative amount`() {
        StepVerifier.create(service.createIntent(-5.0, "eur", credentials, merchantId, null, "ORDER-1", paymentRequestId, null))
            .expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("positive") }
            .verify()
    }

    @Test
    fun `rejects NaN amount`() {
        StepVerifier.create(service.createIntent(Double.NaN, "eur", credentials, merchantId, null, "ORDER-1", paymentRequestId, null))
            .expectErrorMatches { it is IllegalArgumentException }
            .verify()
    }

    @Test
    fun `rejects infinite amount`() {
        StepVerifier.create(service.createIntent(Double.POSITIVE_INFINITY, "eur", credentials, merchantId, null, "ORDER-1", paymentRequestId, null))
            .expectErrorMatches { it is IllegalArgumentException }
            .verify()
    }
}
