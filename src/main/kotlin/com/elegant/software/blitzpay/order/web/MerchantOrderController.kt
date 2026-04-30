package com.elegant.software.blitzpay.order.web

import com.elegant.software.blitzpay.order.api.CreateMerchantOrderRequest
import com.elegant.software.blitzpay.order.api.MerchantOrderResponse
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.application.OrderCreationConflictException
import com.elegant.software.blitzpay.order.application.OrderService
import com.elegant.software.blitzpay.order.domain.OrderStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

@Tag(name = "Merchant Orders", description = "Create and list orders for authenticated merchant branches")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchant/orders", version = "1")
class MerchantOrderController(
    private val orderService: OrderService,
) {
    @Operation(summary = "Create an order from branch products and return a QR code for customer payment")
    @PostMapping
    fun create(
        @RequestBody request: CreateMerchantOrderRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): Mono<ResponseEntity<MerchantOrderResponse>> {
        val merchantUserId = extractSubject(authorization) ?: "anonymous"
        return Mono.fromCallable { orderService.createMerchantOrder(request, merchantUserId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }
    }

    @Operation(summary = "List today's orders for the given branch, with optional status filter")
    @GetMapping
    fun list(
        @RequestParam branchId: UUID,
        @RequestParam(required = false) status: OrderStatus?,
        @RequestParam(required = false, defaultValue = "UTC") timezone: String,
    ): Mono<ResponseEntity<List<OrderResponse>>> {
        val zoneId = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
        return Mono.fromCallable { orderService.listMerchantOrders(branchId, status, zoneId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }
    }

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
