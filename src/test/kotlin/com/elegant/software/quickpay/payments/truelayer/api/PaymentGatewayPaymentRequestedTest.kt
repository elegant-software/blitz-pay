package com.elegant.software.quickpay.payments.truelayer.api // <- adjust to your module's base package

import com.elegant.software.quickpay.payments.truelayer.outbound.PaymentService
import com.elegant.software.quickpay.payments.truelayer.support.TrueLayerProperties
import com.truelayer.java.TrueLayerClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.modulith.test.Scenario


@ApplicationModuleTest(verifyAutomatically = false, mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
class TrueLayerPaymentStarterTests {

    @Autowired
    lateinit var gateway: PaymentService

    @MockBean
    lateinit var trueLayerClient: TrueLayerClient

    @MockBean
    open lateinit var trueLayerProperties: TrueLayerProperties

    // Optional: if your PaymentGateway returns a String, stub it (not strictly required for this test)
    private fun stubGateway() {
        val mockResult = PaymentResult(
            paymentRequestId = java.util.UUID.randomUUID(),
            orderId = "order-42",
            paymentId = "tl-payment-123"
        )
        whenever(gateway.startPayment(any())).thenReturn(mockResult)
    }

    @Test
    fun `listener calls gateway with mapped StartPaymentCommand`(scenario: Scenario) {
        stubGateway()

        val event = PaymentRequested(
            paymentRequestId = java.util.UUID.randomUUID(),
            orderId = "order-42",
            amountMinorUnits = 12_34,     // e.g., 12.34 in minor units
            currency = "EUR",
            userDisplayName = "Ada Lovelace",
            redirectReturnUri = "https://app.example.com/payments/return"
        )

        // Publish the domain event inside Scenario. Although @EventListener is sync by default,
        // we use andWaitForStateChange to robustly wait until the mock has been invoked.
        scenario
            .publish(event)
            .andWaitForStateChange {
                // Poll the number of times startPayment was called
                Mockito.mockingDetails(gateway).invocations.count { it.method.name == "startPayment" }
            }
            .andVerify { callCount ->
                assertThat(callCount).isEqualTo(1)

                // Capture and assert the exact command mapped by the listener
                val cmdCaptor = argumentCaptor<PaymentRequested>()
                verify(gateway).startPayment(cmdCaptor.capture())

                val cmd = cmdCaptor.firstValue
                assertThat(cmd.orderId).isEqualTo("order-42")
                assertThat(cmd.amountMinorUnits).isEqualTo(12_34)
                assertThat(cmd.currency).isEqualTo("EUR")
                assertThat(cmd.userDisplayName).isEqualTo("Ada Lovelace")
                //assertThat(cmd.returnUri).isEqualTo("https://app.example.com/payments/return")
            }
    }
}
