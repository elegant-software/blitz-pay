package com.elegant.software.blitzpay.order.application

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.order.api.CreateMerchantOrderRequest
import com.elegant.software.blitzpay.order.api.CreateOrderRequest
import com.elegant.software.blitzpay.order.api.MerchantOrderResponse
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.api.OrderSummaryResponse
import com.elegant.software.blitzpay.order.api.toMerchantResponse
import com.elegant.software.blitzpay.order.api.toResponse
import com.elegant.software.blitzpay.order.api.toSummaryResponse
import com.elegant.software.blitzpay.order.domain.CreatorType
import com.elegant.software.blitzpay.order.domain.Order
import com.elegant.software.blitzpay.order.domain.OrderItem
import com.elegant.software.blitzpay.order.domain.OrderStatus
import com.elegant.software.blitzpay.order.domain.unitPriceMinor
import com.elegant.software.blitzpay.order.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Transactional
class OrderService(
    private val merchantGateway: MerchantGateway,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
) {
    private val log = LoggerFactory.getLogger(OrderService::class.java)

    fun createShopperOrder(request: CreateOrderRequest, shopperId: String): OrderResponse {
        require(request.items.isNotEmpty()) { "Order must contain at least one item" }
        request.items.forEach { require(it.quantity > 0) { "quantity must be > 0 for product ${it.productId}" } }

        val products = merchantGateway.findOrderableProducts(request.items.map { it.productId }.distinct())
        val productsById = products.associateBy { it.productId }
        validateProducts(request, productsById)

        val order = Order(
            orderId = Order.nextOrderId(),
            merchantApplicationId = products.map { it.merchantApplicationId }.distinct().single(),
            merchantBranchId = products.mapNotNull { it.branchId }.distinct().singleOrNull(),
            creatorType = CreatorType.SHOPPER,
            createdById = shopperId,
            currency = DEFAULT_CURRENCY,
            totalAmountMinor = request.items.sumOf { productsById.getValue(it.productId).unitPriceMinor() * it.quantity },
            itemCount = request.items.sumOf { it.quantity },
        )
        val savedOrder = orderRepository.save(order)
        val savedItems = orderItemRepository.saveAll(
            request.items.map { OrderItem.fromProduct(savedOrder.id, productsById.getValue(it.productId), it.quantity) }
        )
        LogContext.with(LogContext.ORDER_ID to savedOrder.orderId) {
            log.info(
                "order saved orderId={} merchantId={} branchId={} totalAmountMinor={} currency={} itemCount={}",
                savedOrder.orderId, savedOrder.merchantApplicationId, savedOrder.merchantBranchId,
                savedOrder.totalAmountMinor, savedOrder.currency, savedOrder.itemCount,
            )
        }
        return savedOrder.toResponse(savedItems)
    }

    fun createMerchantOrder(request: CreateMerchantOrderRequest, merchantUserId: String): MerchantOrderResponse {
        require(request.items.isNotEmpty()) { "Order must contain at least one item" }
        request.items.forEach { require(it.quantity > 0) { "quantity must be > 0 for product ${it.productId}" } }

        val products = merchantGateway.findOrderableProducts(request.items.map { it.productId }.distinct())
        val productsById = products.associateBy { it.productId }
        validateProductsForMerchant(request, productsById)

        val order = Order(
            orderId = Order.nextOrderId(),
            merchantApplicationId = request.merchantId,
            merchantBranchId = request.branchId,
            creatorType = CreatorType.MERCHANT,
            createdById = merchantUserId,
            currency = DEFAULT_CURRENCY,
            totalAmountMinor = request.items.sumOf { productsById.getValue(it.productId).unitPriceMinor() * it.quantity },
            itemCount = request.items.sumOf { it.quantity },
        )
        val savedOrder = orderRepository.save(order)
        val savedItems = orderItemRepository.saveAll(
            request.items.map { OrderItem.fromProduct(savedOrder.id, productsById.getValue(it.productId), it.quantity) }
        )

        val qrUrl = buildQrPaymentUrl(savedOrder.orderId, savedOrder.totalAmountMinor, savedOrder.currency)
        return savedOrder.toMerchantResponse(savedItems, qrUrl)
    }

    @Transactional(readOnly = true)
    fun get(orderId: String): OrderResponse {
        val order = orderRepository.findByOrderId(orderId)
            ?: throw NoSuchElementException("Order not found: $orderId")
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        return order.toResponse(items)
    }

    @Transactional(readOnly = true)
    fun listShopperOrders(shopperId: String): List<OrderSummaryResponse> {
        val cutoff = Instant.now().minus(7, ChronoUnit.DAYS)
        return orderRepository
            .findByCreatedByIdAndCreatorTypeAndCreatedAtAfter(shopperId, CreatorType.SHOPPER, cutoff)
            .map { it.toSummaryResponse() }
    }

    @Transactional(readOnly = true)
    fun listMerchantOrders(branchId: UUID, status: OrderStatus?, timezone: ZoneId = ZoneId.of("UTC")): List<OrderResponse> {
        val today = LocalDate.now(timezone)
        val from = today.atStartOfDay(timezone).toInstant()
        val to = today.plusDays(1).atStartOfDay(timezone).toInstant()
        val orders = if (status != null) {
            orderRepository.findByMerchantBranchIdAndStatusAndCreatedAtBetween(branchId, status, from, to)
        } else {
            orderRepository.findByMerchantBranchIdAndCreatedAtBetween(branchId, from, to)
        }
        return orders.map { order ->
            val items = orderItemRepository.findAllByOrderIdFk(order.id)
            order.toResponse(items)
        }
    }

    private fun validateProducts(
        request: CreateOrderRequest,
        productsById: Map<UUID, com.elegant.software.blitzpay.merchant.api.OrderableMerchantProduct>,
    ) {
        val requestedIds = request.items.map { it.productId }.distinct()
        val missing = requestedIds.filterNot(productsById::containsKey)
        if (missing.isNotEmpty()) throw NoSuchElementException("Products not found: ${missing.joinToString(",")}")

        val inactive = productsById.values.filterNot { it.active }.map { it.productId }
        if (inactive.isNotEmpty()) throw OrderCreationConflictException("Products are not orderable: ${inactive.joinToString(",")}")

        val merchantIds = productsById.values.map { it.merchantApplicationId }.distinct()
        if (merchantIds.size != 1) throw OrderCreationConflictException("All ordered products must belong to the same merchant")
    }

    private fun validateProductsForMerchant(
        request: CreateMerchantOrderRequest,
        productsById: Map<UUID, com.elegant.software.blitzpay.merchant.api.OrderableMerchantProduct>,
    ) {
        val requestedIds = request.items.map { it.productId }.distinct()
        val missing = requestedIds.filterNot(productsById::containsKey)
        if (missing.isNotEmpty()) throw NoSuchElementException("Products not found: ${missing.joinToString(",")}")

        val inactive = productsById.values.filterNot { it.active }.map { it.productId }
        if (inactive.isNotEmpty()) throw OrderCreationConflictException("Products are not orderable: ${inactive.joinToString(",")}")

        val wrongMerchant = productsById.values.filter { it.merchantApplicationId != request.merchantId }.map { it.productId }
        if (wrongMerchant.isNotEmpty()) throw OrderCreationConflictException("Products do not belong to merchant: ${wrongMerchant.joinToString(",")}")
    }

    private fun buildQrPaymentUrl(orderId: String, amountMinorUnits: Long, currency: String): String =
        "blitzpay://payment/qr?orderId=$orderId&amount=$amountMinorUnits&currency=$currency"

    companion object {
        private const val DEFAULT_CURRENCY = "EUR"
    }
}

class OrderCreationConflictException(message: String) : IllegalStateException(message)
