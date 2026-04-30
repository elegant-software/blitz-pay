package com.elegant.software.blitzpay.order.api

import com.elegant.software.blitzpay.order.domain.CreatorType
import com.elegant.software.blitzpay.order.domain.Order
import com.elegant.software.blitzpay.order.domain.OrderItem
import com.elegant.software.blitzpay.order.domain.OrderStatus
import java.time.Instant
import java.util.UUID

enum class PaymentMethod { TRUELAYER, QRPAY, BRAINTREE, STRIPE }

data class CreateOrderRequest(
    val items: List<CreateOrderItemRequest> = emptyList(),
    val paymentMethod: PaymentMethod = PaymentMethod.TRUELAYER,
)

data class CreateMerchantOrderRequest(
    val merchantId: UUID,
    val branchId: UUID? = null,
    val items: List<CreateOrderItemRequest> = emptyList(),
)

data class CreateOrderItemRequest(
    val productId: UUID,
    val quantity: Int,
)

data class OrderItemResponse(
    val productId: UUID,
    val name: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
)

data class PaymentReferenceResponse(
    val provider: String,
    val paymentRequestId: UUID,
)

data class QrCodeResponse(
    val paymentUrl: String,
)

data class OrderResponse(
    val orderId: String,
    val merchantId: UUID,
    val branchId: UUID?,
    val status: OrderStatus,
    val creatorType: CreatorType,
    val createdById: String,
    val currency: String,
    val totalAmountMinor: Long,
    val paymentRetryAllowed: Boolean,
    val items: List<OrderItemResponse>,
    val createdAt: Instant,
    val lastPaymentRequestId: UUID? = null,
    val lastPaymentProvider: String? = null,
    val paidAt: Instant? = null,
    val paymentReference: PaymentReferenceResponse? = null,
)

data class MerchantOrderResponse(
    val orderId: String,
    val merchantId: UUID,
    val branchId: UUID?,
    val status: OrderStatus,
    val creatorType: CreatorType,
    val currency: String,
    val totalAmountMinor: Long,
    val paymentRetryAllowed: Boolean,
    val qrCode: QrCodeResponse,
    val items: List<OrderItemResponse>,
    val createdAt: Instant,
)

data class OrderSummaryResponse(
    val orderId: String,
    val merchantId: UUID,
    val branchId: UUID?,
    val status: OrderStatus,
    val currency: String,
    val totalAmountMinor: Long,
    val paymentRetryAllowed: Boolean,
    val createdAt: Instant,
)

data class OrderPaymentInitiationRequested(
    val orderId: String,
    val paymentMethod: PaymentMethod,
    val amountMinorUnits: Long,
    val currency: String,
    val paymentRequestId: UUID,
    val merchantId: UUID,
    val branchId: UUID?,
    val redirectReturnUri: String = "blitzpay://payment/return",
)

internal fun Order.toResponse(items: List<OrderItem>, paymentReference: PaymentReferenceResponse? = null) = OrderResponse(
    orderId = orderId,
    merchantId = merchantApplicationId,
    branchId = merchantBranchId,
    status = status,
    creatorType = creatorType,
    createdById = createdById,
    currency = currency,
    totalAmountMinor = totalAmountMinor,
    paymentRetryAllowed = status.paymentRetryAllowed,
    items = items.sortedBy { it.createdAt }.map {
        OrderItemResponse(
            productId = it.merchantProductId,
            name = it.productName,
            quantity = it.quantity,
            unitPriceMinor = it.unitPriceMinor,
            lineTotalMinor = it.lineTotalMinor,
        )
    },
    createdAt = createdAt,
    lastPaymentRequestId = lastPaymentRequestId,
    lastPaymentProvider = lastPaymentProvider,
    paidAt = paidAt,
    paymentReference = paymentReference,
)

internal fun Order.toSummaryResponse() = OrderSummaryResponse(
    orderId = orderId,
    merchantId = merchantApplicationId,
    branchId = merchantBranchId,
    status = status,
    currency = currency,
    totalAmountMinor = totalAmountMinor,
    paymentRetryAllowed = status.paymentRetryAllowed,
    createdAt = createdAt,
)

internal fun Order.toMerchantResponse(items: List<OrderItem>, qrPaymentUrl: String) = MerchantOrderResponse(
    orderId = orderId,
    merchantId = merchantApplicationId,
    branchId = merchantBranchId,
    status = status,
    creatorType = creatorType,
    currency = currency,
    totalAmountMinor = totalAmountMinor,
    paymentRetryAllowed = status.paymentRetryAllowed,
    qrCode = QrCodeResponse(paymentUrl = qrPaymentUrl),
    items = items.sortedBy { it.createdAt }.map {
        OrderItemResponse(
            productId = it.merchantProductId,
            name = it.productName,
            quantity = it.quantity,
            unitPriceMinor = it.unitPriceMinor,
            lineTotalMinor = it.lineTotalMinor,
        )
    },
    createdAt = createdAt,
)
