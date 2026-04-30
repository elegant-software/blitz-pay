package com.elegant.software.blitzpay.order.application

import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.merchant.api.OrderableMerchantProduct
import com.elegant.software.blitzpay.order.OrderFixtureLoader
import com.elegant.software.blitzpay.order.api.CreateMerchantOrderRequest
import com.elegant.software.blitzpay.order.api.CreateOrderItemRequest
import com.elegant.software.blitzpay.order.api.PaymentMethod
import com.elegant.software.blitzpay.order.domain.Order
import com.elegant.software.blitzpay.order.domain.OrderItem
import com.elegant.software.blitzpay.order.domain.OrderStatus
import com.elegant.software.blitzpay.order.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.repository.OrderRepository
import com.elegant.software.blitzpay.order.repository.PaymentAttemptRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class OrderServiceTest {
    private val merchantGateway = mock<MerchantGateway>()
    private val orderRepository = mock<OrderRepository>()
    private val orderItemRepository = mock<OrderItemRepository>()
    private val paymentAttemptRepository = mock<PaymentAttemptRepository>()
    private val service = OrderService(
        merchantGateway, orderRepository, orderItemRepository,
    )

    private val merchantId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val branchId = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @Test
    fun `createShopperOrder persists order snapshots and totals`() {
        val request = OrderFixtureLoader.createOrderRequest()
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true),
                OrderableMerchantProduct(request.items[1].productId, merchantId, branchId, "Bagel", "Sesame", BigDecimal("4.00"), true),
            )
        )
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }
        val response = service.createShopperOrder(request, "shopper-abc")

        assertEquals(2_900L, response.totalAmountMinor)
        assertEquals(merchantId, response.merchantId)
        assertEquals(branchId, response.branchId)
        assertEquals(2, response.items.size)
        assertEquals("Coffee", response.items.first().name)
        assertEquals(2_500L, response.items.first().lineTotalMinor)
        assertEquals(OrderStatus.CREATED, response.status)
        assertEquals(PaymentMethod.TRUELAYER, request.paymentMethod)
        assertNull(response.paymentReference)
        assertNull(response.lastPaymentRequestId)
        assertNull(response.lastPaymentProvider)
        verify(paymentAttemptRepository, never()).save(any())
    }

    @Test
    fun `createShopperOrder rejects missing products`() {
        val request = OrderFixtureLoader.createOrderRequest()
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(emptyList())

        assertFailsWith<NoSuchElementException> {
            service.createShopperOrder(request, "shopper-abc")
        }
    }

    @Test
    fun `createShopperOrder rejects inactive products`() {
        val request = OrderFixtureLoader.createOrderRequest()
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, merchantId, null, "Coffee", null, BigDecimal("12.50"), false),
                OrderableMerchantProduct(request.items[1].productId, merchantId, null, "Bagel", null, BigDecimal("4.00"), true),
            )
        )

        assertFailsWith<OrderCreationConflictException> {
            service.createShopperOrder(request, "shopper-abc")
        }
    }

    @Test
    fun `createShopperOrder rejects cross merchant products`() {
        val request = OrderFixtureLoader.createOrderRequest()
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, UUID.randomUUID(), null, "Coffee", null, BigDecimal("12.50"), true),
                OrderableMerchantProduct(request.items[1].productId, UUID.randomUUID(), null, "Bagel", null, BigDecimal("4.00"), true),
            )
        )

        assertFailsWith<OrderCreationConflictException> {
            service.createShopperOrder(request, "shopper-abc")
        }
    }

    @Test
    fun `createMerchantOrder returns CREATED status with QR code`() {
        val productId1 = UUID.fromString("22222222-2222-2222-2222-222222222221")
        val productId2 = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val request = CreateMerchantOrderRequest(
            merchantId = merchantId,
            branchId = branchId,
            items = listOf(
                CreateOrderItemRequest(productId1, 2),
                CreateOrderItemRequest(productId2, 1),
            ),
        )
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(productId1, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true),
                OrderableMerchantProduct(productId2, merchantId, branchId, "Bagel", "Sesame", BigDecimal("4.00"), true),
            )
        )
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }

        val response = service.createMerchantOrder(request, "merchant-user-xyz")

        assertEquals(OrderStatus.CREATED, response.status)
        assertEquals(2_900L, response.totalAmountMinor)
        assertEquals(merchantId, response.merchantId)
        assert(response.qrCode.paymentUrl.contains("blitzpay://payment/qr"))
    }
}
