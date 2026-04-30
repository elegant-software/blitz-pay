package com.elegant.software.blitzpay.order

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.order.api.MerchantOrderResponse
import com.elegant.software.blitzpay.order.api.OrderItemResponse
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.api.OrderSummaryResponse
import com.elegant.software.blitzpay.order.api.PaymentMethod
import com.elegant.software.blitzpay.order.api.QrCodeResponse
import com.elegant.software.blitzpay.order.application.OrderService
import com.elegant.software.blitzpay.order.domain.CreatorType
import com.elegant.software.blitzpay.order.domain.OrderStatus
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.util.UUID

class OrderContractTest : ContractVerifierBase() {
    @MockitoBean
    private lateinit var orderService: OrderService

    private val merchantId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val branchId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    @Test
    fun `post orders returns created order without payment reference`() {
        whenever(orderService.createShopperOrder(any(), any())).thenReturn(
            OrderResponse(
                orderId = "ORD-ABC123456789",
                merchantId = merchantId,
                branchId = branchId,
                status = OrderStatus.CREATED,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-abc",
                currency = "EUR",
                totalAmountMinor = 2900,
                paymentRetryAllowed = true,
                items = listOf(
                    OrderItemResponse(
                        productId = UUID.fromString("22222222-2222-2222-2222-222222222221"),
                        name = "Coffee",
                        quantity = 2,
                        unitPriceMinor = 1250,
                        lineTotalMinor = 2500,
                    )
                ),
                createdAt = Instant.parse("2026-04-30T10:00:00Z"),
            )
        )

        webTestClient.post()
            .uri("/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "items": [
                    { "productId": "22222222-2222-2222-2222-222222222221", "quantity": 2 },
                    { "productId": "22222222-2222-2222-2222-222222222222", "quantity": 1 }
                  ],
                  "paymentMethod": "TRUELAYER"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("ORD-ABC123456789")
            .jsonPath("$.status").isEqualTo("CREATED")
            .jsonPath("$.paymentRetryAllowed").isEqualTo(true)
            .jsonPath("$.paymentReference").doesNotExist()
            .jsonPath("$.totalAmountMinor").isEqualTo(2900)
    }

    @Test
    fun `post merchant orders returns created status with qr code`() {
        whenever(orderService.createMerchantOrder(any(), any())).thenReturn(
            MerchantOrderResponse(
                orderId = "ORD-MERCHANT00001",
                merchantId = merchantId,
                branchId = branchId,
                status = OrderStatus.CREATED,
                creatorType = CreatorType.MERCHANT,
                currency = "EUR",
                totalAmountMinor = 1250,
                paymentRetryAllowed = true,
                qrCode = QrCodeResponse(paymentUrl = "blitzpay://payment/qr?orderId=ORD-MERCHANT00001&amount=1250&currency=EUR"),
                items = listOf(
                    OrderItemResponse(
                        productId = UUID.fromString("22222222-2222-2222-2222-222222222221"),
                        name = "Coffee",
                        quantity = 1,
                        unitPriceMinor = 1250,
                        lineTotalMinor = 1250,
                    )
                ),
                createdAt = Instant.parse("2026-04-30T10:00:00Z"),
            )
        )

        webTestClient.post()
            .uri("/v1/merchant/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "merchantId": "11111111-1111-1111-1111-111111111111",
                  "branchId": "33333333-3333-3333-3333-333333333333",
                  "items": [
                    { "productId": "22222222-2222-2222-2222-222222222221", "quantity": 1 }
                  ]
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("ORD-MERCHANT00001")
            .jsonPath("$.status").isEqualTo("CREATED")
            .jsonPath("$.paymentRetryAllowed").isEqualTo(true)
            .jsonPath("$.qrCode.paymentUrl").isEqualTo("blitzpay://payment/qr?orderId=ORD-MERCHANT00001&amount=1250&currency=EUR")
    }

    @Test
    fun `get order returns paid status`() {
        whenever(orderService.get("ORD-ABC123456789")).thenReturn(
            OrderResponse(
                orderId = "ORD-ABC123456789",
                merchantId = merchantId,
                branchId = null,
                status = OrderStatus.PAID,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-abc",
                currency = "EUR",
                totalAmountMinor = 2900,
                paymentRetryAllowed = false,
                items = emptyList(),
                createdAt = Instant.parse("2026-04-30T10:00:00Z"),
                paidAt = Instant.parse("2026-04-30T10:15:30Z"),
            )
        )

        webTestClient.get()
            .uri("/v1/orders/ORD-ABC123456789")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("PAID")
            .jsonPath("$.paymentRetryAllowed").isEqualTo(false)
            .jsonPath("$.paidAt").isEqualTo("2026-04-30T10:15:30Z")
    }

    @Test
    fun `get shopper orders returns summary list`() {
        whenever(orderService.listShopperOrders(any())).thenReturn(
            listOf(
                OrderSummaryResponse(
                    orderId = "ORD-ABC123456789",
                    merchantId = merchantId,
                    branchId = branchId,
                    status = OrderStatus.PAID,
                    currency = "EUR",
                    totalAmountMinor = 2900,
                    paymentRetryAllowed = false,
                    createdAt = Instant.parse("2026-04-30T10:00:00Z"),
                )
            )
        )

        webTestClient.get()
            .uri("/v1/orders")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].orderId").isEqualTo("ORD-ABC123456789")
            .jsonPath("$[0].status").isEqualTo("PAID")
            .jsonPath("$[0].paymentRetryAllowed").isEqualTo(false)
    }
}
