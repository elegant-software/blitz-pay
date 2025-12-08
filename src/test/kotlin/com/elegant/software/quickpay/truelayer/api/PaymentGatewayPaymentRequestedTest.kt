package com.elegant.software.quickpay.truelayer.api // <- adjust to your module's base package

import com.elegant.software.quickpay.payments.truelayer.outbound.PaymentService
import com.elegant.software.quickpay.payments.truelayer.support.TrueLayerProperties
import com.truelayer.java.TrueLayerClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.modulith.test.Scenario
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID


@ApplicationModuleTest(verifyAutomatically = false, mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
class TrueLayerPaymentStarterTests {

    @Mock
    lateinit var gateway: PaymentService

    @Mock
    lateinit var trueLayerClient: TrueLayerClient

    @Autowired
    lateinit var trueLayerProperties: TrueLayerProperties

    @Test
    fun `listener calls gateway with mapped StartPaymentCommand`(scenario: Scenario) {
        val result = PaymentResult(
            paymentRequestId = UUID.randomUUID(),
            status = "executed",
            transactionId = "tx-123",
            amount = 12.34,
            currency = "EUR",
            qrCodeData = null,
            qrStatus = null,
            deepLink = null
        )
        whenever(gateway.startPayment(any())).thenReturn(result)

        val event = PaymentRequested(
            orderId = "order-42",
            amount = BigDecimal("15.97"),
            currency = "EUR",
            userDisplayName = "Ada Lovelace",
            redirectReturnUri = "https://app.example.com/payments/return",
            createdAt = Instant.now(),
            paymentRequestId = UUID.randomUUID(),
            merchant = "",
            customerName = "",
            customerEmail = "",
        )

        scenario
            .publish(event)
            .andWaitForStateChange {
                Mockito.mockingDetails(gateway).invocations.count { it.method.name == "startPayment" }
            }
            .andVerify { callCount ->
                assertThat(callCount).isEqualTo(1)
            }
    }
}
