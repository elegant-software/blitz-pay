package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.elegant.software.blitzpay.payments.stripe.internal.StripePaymentService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.test.StepVerifier

class StripePaymentServiceTest {

    private val properties = StripeProperties(
        secretKey = "sk_test_dummy",
        publishableKey = "pk_test_dummy",
    )
    private val service = StripePaymentService(properties)

    @Test
    fun `rejects zero amount`() {
        val mono = service.createIntent(0.0, "eur")
        StepVerifier.create(mono)
            .expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("positive") }
            .verify()
    }

    @Test
    fun `rejects negative amount`() {
        val mono = service.createIntent(-5.0, "eur")
        StepVerifier.create(mono)
            .expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("positive") }
            .verify()
    }

    @Test
    fun `rejects NaN amount`() {
        val mono = service.createIntent(Double.NaN, "eur")
        StepVerifier.create(mono)
            .expectErrorMatches { it is IllegalArgumentException }
            .verify()
    }

    @Test
    fun `rejects infinite amount`() {
        val mono = service.createIntent(Double.POSITIVE_INFINITY, "eur")
        StepVerifier.create(mono)
            .expectErrorMatches { it is IllegalArgumentException }
            .verify()
    }
}
