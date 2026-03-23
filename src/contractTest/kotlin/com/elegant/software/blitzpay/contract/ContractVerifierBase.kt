package com.elegant.software.blitzpay.contract

import com.elegant.software.blitzpay.payments.QuickpayApplication
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentRequested
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentResult
import com.elegant.software.blitzpay.payments.truelayer.outbound.PaymentService
import com.elegant.software.blitzpay.payments.truelayer.support.JwksService
import com.truelayer.java.TrueLayerClient
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.URI

@SpringBootTest(
    classes = [QuickpayApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("contract-test")
abstract class ContractVerifierBase {

    @LocalServerPort
    private var port: Int = 0

    protected lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var paymentService: PaymentService

    @MockitoBean
    private lateinit var trueLayerClient: TrueLayerClient

    @MockitoBean
    private lateinit var jwksService: JwksService

    @BeforeEach
    fun setupRestAssured() {
        whenever(paymentService.startPayment(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<PaymentRequested>(0)
            PaymentResult(
                paymentRequestId = requireNotNull(request.paymentRequestId),
                orderId = request.orderId,
                paymentId = "contract-test-payment-id",
                redirectURI = URI.create("https://contract-test.blitzpay.local/payments/${request.paymentRequestId}")
            )
        }
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }
}
