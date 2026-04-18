package com.elegant.software.blitzpay.payments.braintree.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

data class ClientTokenResponse(val clientToken: String)
data class CheckoutRequest(
    val nonce: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val invoiceId: String? = null,
)
data class CheckoutSuccessResponse(
    val status: String = "succeeded",
    val transactionId: String,
    val amount: String,
    val currency: String,
    val invoiceId: String? = null,
)
data class CheckoutFailureResponse(
    val status: String = "failed",
    val message: String,
    val code: String? = null,
)
data class BraintreeErrorResponse(val error: String)

@Tag(name = "Braintree", description = "PayPal / digital wallet payments via Braintree")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/payments/braintree", version = "1")
class BraintreePaymentController(private val braintreePaymentService: BraintreePaymentService) {

    @Operation(summary = "Generate a Braintree client token for the mobile SDK.")
    @PostMapping("/client-token")
    fun clientToken(): Mono<ResponseEntity<Any>> {
        if (!braintreePaymentService.isConfigured()) {
            return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(BraintreeErrorResponse("Braintree not configured on server") as Any)
            )
        }
        return braintreePaymentService.generateClientToken()
            .map { token -> ResponseEntity.ok(ClientTokenResponse(token) as Any) }
            .onErrorResume { ex ->
                Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(BraintreeErrorResponse(ex.message ?: "Braintree client token generation failed") as Any)
                )
            }
    }

    @Operation(summary = "Submit a Braintree payment nonce for settlement.")
    @PostMapping("/checkout")
    fun checkout(@RequestBody request: CheckoutRequest): Mono<ResponseEntity<Any>> {
        if (!braintreePaymentService.isConfigured()) {
            return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(BraintreeErrorResponse("Braintree not configured on server") as Any)
            )
        }
        val nonce = request.nonce
        val amount = request.amount
        if (nonce.isNullOrBlank() || amount == null || amount <= 0 || !amount.isFinite()) {
            return Mono.just(
                ResponseEntity.badRequest()
                    .body(BraintreeErrorResponse("nonce and amount are required") as Any)
            )
        }
        val currency = request.currency ?: "EUR"
        return braintreePaymentService.checkout(nonce, amount, currency, request.invoiceId)
            .map { result ->
                when (result) {
                    is BraintreeCheckoutResult.Success -> ResponseEntity.ok(
                        CheckoutSuccessResponse(
                            transactionId = result.transactionId,
                            amount = result.amount,
                            currency = result.currency,
                            invoiceId = result.invoiceId,
                        ) as Any
                    )
                    is BraintreeCheckoutResult.Failure -> ResponseEntity.ok(
                        CheckoutFailureResponse(message = result.message, code = result.code) as Any
                    )
                }
            }
            .onErrorResume { ex ->
                Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(BraintreeErrorResponse(ex.message ?: "Braintree sale failed") as Any)
                )
            }
    }
}
