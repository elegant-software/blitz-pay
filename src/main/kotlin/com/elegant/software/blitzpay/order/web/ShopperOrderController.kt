package com.elegant.software.blitzpay.order.web

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.order.api.CreateOrderRequest
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.api.OrderSummaryResponse
import com.elegant.software.blitzpay.order.application.OrderCreationConflictException
import com.elegant.software.blitzpay.order.application.OrderService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.Base64

@Tag(name = "Orders", description = "Create and inspect orders for shoppers")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/orders", version = "1")
class ShopperOrderController(
    private val orderService: OrderService,
) {
    private val log = LoggerFactory.getLogger(ShopperOrderController::class.java)

    @Operation(summary = "Create an order and initiate payment via the chosen provider")
    @PostMapping
    fun create(
        @RequestBody request: CreateOrderRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): Mono<ResponseEntity<OrderResponse>> {
        val shopperId = extractSubject(authorization) ?: "anonymous"
        return Mono.fromCallable {
            log.info(
                "order create request shopperId={} itemCount={} productIds={}",
                shopperId, request.items.size, request.items.map { it.productId },
            )
            orderService.createShopperOrder(request, shopperId)
        }
            .subscribeOn(Schedulers.boundedElastic())
            .map { response ->
                LogContext.with(LogContext.ORDER_ID to response.orderId) {
                    log.info(
                        "order created orderId={} totalAmountMinor={} currency={} itemCount={}",
                        response.orderId, response.totalAmountMinor, response.currency, response.items.size,
                    )
                }
                ResponseEntity.status(HttpStatus.CREATED).body(response)
            }
    }

    @Operation(summary = "List recent orders for the authenticated shopper (last 7 days)")
    @GetMapping
    fun list(
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): Mono<ResponseEntity<List<OrderSummaryResponse>>> {
        val shopperId = extractSubject(authorization) ?: "anonymous"
        return Mono.fromCallable { orderService.listShopperOrders(shopperId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }
    }

    @Operation(summary = "Get the latest business status for an order")
    @GetMapping("/{orderId}")
    fun get(@PathVariable orderId: String): Mono<ResponseEntity<OrderResponse>> =
        Mono.fromCallable { orderService.get(orderId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(ex: IllegalArgumentException): ResponseEntity<OrderErrorResponse> =
        ResponseEntity.badRequest().body(OrderErrorResponse(ex.message ?: "Invalid order request"))

    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(ex: NoSuchElementException): ResponseEntity<OrderErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(OrderErrorResponse(ex.message ?: "Order not found"))

    @ExceptionHandler(OrderCreationConflictException::class)
    fun unprocessable(ex: OrderCreationConflictException): ResponseEntity<OrderErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(OrderErrorResponse(ex.message ?: "Order cannot be created"))

    private fun extractSubject(authorization: String?): String? {
        val token = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val parts = token.split('.')
        if (parts.size < 2) return null

        return runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]))
        }.mapCatching { payload ->
            Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.getOrNull(1)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(512)
    }
}
