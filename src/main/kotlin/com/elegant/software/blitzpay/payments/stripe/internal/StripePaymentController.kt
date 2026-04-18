package com.elegant.software.blitzpay.payments.stripe.internal

import com.stripe.exception.StripeException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

data class CreateIntentRequest(val amount: Double? = null, val currency: String? = null)
data class CreateIntentResponse(val paymentIntent: String, val publishableKey: String)
data class ErrorResponse(val error: String)

@Tag(name = "Stripe", description = "Card payment session creation via Stripe")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/payments/stripe", version = "1")
class StripePaymentController(private val stripePaymentService: StripePaymentService) {

    @Operation(summary = "Create a Stripe PaymentIntent and return credentials to the mobile SDK.")
    @PostMapping("/create-intent")
    fun createIntent(@RequestBody request: CreateIntentRequest): Mono<ResponseEntity<Any>> {
        val amount = request.amount
        if (amount == null || amount <= 0 || !amount.isFinite()) {
            return Mono.just(
                ResponseEntity.badRequest().body(ErrorResponse("amount must be a positive number") as Any)
            )
        }
        val currency = request.currency ?: "eur"
        return stripePaymentService.createIntent(amount, currency)
            .map { result ->
                ResponseEntity.ok(
                    CreateIntentResponse(result.clientSecret, result.publishableKey) as Any
                )
            }
            .onErrorResume(IllegalArgumentException::class.java) { ex ->
                Mono.just(ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "bad request") as Any))
            }
            .onErrorResume(StripeException::class.java) { ex ->
                Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ErrorResponse(ex.message ?: "Stripe error") as Any)
                )
            }
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(ex.message ?: "unexpected error"))
}
