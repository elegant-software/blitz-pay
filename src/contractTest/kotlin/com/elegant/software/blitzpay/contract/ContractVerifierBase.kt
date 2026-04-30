package com.elegant.software.blitzpay.contract

import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderPaymentSummary
import com.elegant.software.blitzpay.order.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.repository.OrderRepository
import com.elegant.software.blitzpay.order.repository.PaymentAttemptRepository
import com.elegant.software.blitzpay.payments.QuickpayApplication
import com.elegant.software.blitzpay.merchant.application.MerchantProductCategoryService
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import com.elegant.software.blitzpay.merchant.repository.MonitoringRecordRepository
import com.elegant.software.blitzpay.merchant.repository.ProximityEventRepository
import com.elegant.software.blitzpay.payments.push.persistence.DeviceRegistrationRepository
import com.elegant.software.blitzpay.payments.push.persistence.PaymentStatusRepository
import com.elegant.software.blitzpay.payments.push.persistence.ProcessedWebhookEventRepository
import com.elegant.software.blitzpay.payments.push.persistence.PushDeliveryAttemptRepository
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusUpdateGateway
import com.elegant.software.blitzpay.storage.StorageService
import com.elegant.software.blitzpay.support.ContractTestConfig
import com.elegant.software.blitzpay.voice.api.VoiceGateway
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Import
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentRequested
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentResult
import com.elegant.software.blitzpay.payments.truelayer.outbound.PaymentService
import com.elegant.software.blitzpay.payments.truelayer.support.JwksService
import com.truelayer.java.TrueLayerClient
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
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
@Import(ContractTestConfig::class)
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

    @MockitoBean
    protected lateinit var paymentStatusRepository: PaymentStatusRepository

    @MockitoBean
    protected lateinit var deviceRegistrationRepository: DeviceRegistrationRepository

    @MockitoBean
    protected lateinit var processedWebhookEventRepository: ProcessedWebhookEventRepository

    @MockitoBean
    protected lateinit var pushDeliveryAttemptRepository: PushDeliveryAttemptRepository

    @MockitoBean
    protected lateinit var merchantApplicationRepository: MerchantApplicationRepository

    @MockitoBean
    protected lateinit var merchantBranchRepository: MerchantBranchRepository

    @MockitoBean
    protected lateinit var merchantProductRepository: MerchantProductRepository

    @MockitoBean
    protected lateinit var merchantProductCategoryRepository: MerchantProductCategoryRepository

    @MockitoBean
    protected lateinit var merchantProductCategoryService: MerchantProductCategoryService

    @MockitoBean
    protected lateinit var monitoringRecordRepository: MonitoringRecordRepository

    @MockitoBean
    protected lateinit var proximityEventRepository: ProximityEventRepository

    @MockitoBean
    protected lateinit var storageService: StorageService

    @MockitoBean
    protected lateinit var voiceGateway: VoiceGateway

    @MockitoBean
    protected lateinit var paymentStatusInitializationGateway: PaymentStatusInitializationGateway

    @MockitoBean
    protected lateinit var paymentStatusUpdateGateway: PaymentStatusUpdateGateway

    @MockitoBean
    protected lateinit var orderGateway: OrderGateway

    @MockitoBean
    protected lateinit var orderRepository: OrderRepository

    @MockitoBean
    protected lateinit var orderItemRepository: OrderItemRepository

    @MockitoBean
    protected lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @MockitoBean
    protected lateinit var entityManager: EntityManager

    @BeforeEach
    fun setupRestAssured() {
        doNothing().whenever(paymentStatusInitializationGateway).initialize(any(), any(), any(), any(), any())
        doNothing().whenever(paymentStatusUpdateGateway).settle(any(), any(), any())
        doNothing().whenever(paymentStatusUpdateGateway).fail(any(), any(), any())
        whenever(orderGateway.assertPayable(any())).thenReturn(
            OrderPaymentSummary(
                orderId = "ORDER-123",
                merchantId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                branchId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"),
                totalAmountMinor = 1099,
                currency = "EUR"
            )
        )
        whenever(orderGateway.linkPaymentAttempt(any(), any(), any(), anyOrNull())).thenReturn(
            OrderPaymentSummary(
                orderId = "ORDER-123",
                merchantId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                branchId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"),
                totalAmountMinor = 1099,
                currency = "EUR"
            )
        )
        whenever(paymentService.startPayment(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<PaymentRequested>(0)
            PaymentResult(
                paymentRequestId = requireNotNull(request.paymentRequestId),
                orderId = request.orderId,
                paymentId = "contract-test-payment-id",
                resourceToken = "contract-test-resource-token",
                redirectReturnUri = request.redirectReturnUri,
                redirectURI = URI.create("https://contract-test.blitzpay.local/payments/${request.paymentRequestId}")
            )
        }
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }
}
