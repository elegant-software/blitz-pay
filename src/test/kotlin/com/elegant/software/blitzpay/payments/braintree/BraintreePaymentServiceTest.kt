package com.elegant.software.blitzpay.payments.braintree

import com.elegant.software.blitzpay.payments.braintree.internal.BraintreePaymentService
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.Optional

class BraintreePaymentServiceTest {

    private val serviceUnconfigured = BraintreePaymentService(Optional.empty())

    @Test
    fun `isConfigured returns false when gateway absent`() {
        assert(!serviceUnconfigured.isConfigured())
    }

    @Test
    fun `generateClientToken fails when unconfigured`() {
        StepVerifier.create(serviceUnconfigured.generateClientToken())
            .expectErrorMatches { it is IllegalStateException }
            .verify()
    }

    @Test
    fun `checkout rejects blank nonce`() {
        StepVerifier.create(serviceUnconfigured.checkout("", 12.50, "EUR"))
            .expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("nonce") }
            .verify()
    }

    @Test
    fun `checkout rejects zero amount`() {
        StepVerifier.create(serviceUnconfigured.checkout("fake-nonce", 0.0, "EUR"))
            .expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("positive") }
            .verify()
    }

    @Test
    fun `checkout rejects negative amount`() {
        StepVerifier.create(serviceUnconfigured.checkout("fake-nonce", -5.0, "EUR"))
            .expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("positive") }
            .verify()
    }
}
