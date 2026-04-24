package com.elegant.software.blitzpay.payments.braintree.internal

import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

data class ClientTokenRequest(
    val merchantId: UUID? = null,
    val branchId: UUID? = null,
)
data class ClientTokenResponse(val clientToken: String)
data class CheckoutRequest(
    val nonce: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val invoiceId: String? = null,
    val merchantId: UUID? = null,
    val branchId: UUID? = null,
    val productId: UUID? = null,
)
data class CheckoutSuccessResponse(
    val status: String = "succeeded",
    val transactionId: String,
    val amount: String,
    val currency: String,
    val invoiceId: String? = null,
    val merchantId: UUID,
    val branchId: UUID?,
)
data class CheckoutFailureResponse(
    val status: String = "failed",
    val message: String,
    val code: String? = null,
)
data class BraintreeErrorResponse(val error: String)

@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/payments/braintree", version = "1")
class BraintreePaymentController(
    private val braintreePaymentService: BraintreePaymentService,
    private val credentialResolver: MerchantCredentialResolver,
) {

    @Operation(summary = "Generate a Braintree client token for the mobile SDK.")
    @PostMapping("/client-token")
    fun clientToken(@RequestBody request: ClientTokenRequest): Mono<ResponseEntity<Any>> {
        val merchantId = request.merchantId
            ?: return Mono.just(
                ResponseEntity.badRequest().body(BraintreeErrorResponse("merchantId is required") as Any)
            )

        val resolvedBranchId = credentialResolver.resolveBranch(merchantId, request.branchId, null)

        val credentials = credentialResolver.resolveBraintree(merchantId, resolvedBranchId)
            ?: return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(BraintreeErrorResponse("Payment provider not configured") as Any)
            )

        return braintreePaymentService.generateClientToken(credentials)
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
        val merchantId = request.merchantId
            ?: return Mono.just(
                ResponseEntity.badRequest().body(BraintreeErrorResponse("merchantId is required") as Any)
            )

        val resolvedBranchId = credentialResolver.resolveBranch(merchantId, request.branchId, request.productId)
            ?: return Mono.just(
                ResponseEntity.badRequest()
                    .body(BraintreeErrorResponse("branch cannot be resolved: supply branchId or productId") as Any)
            )

        val resolvedAmount = resolveAmount(request) ?: return Mono.just(
            ResponseEntity.badRequest().body(BraintreeErrorResponse("amount must be a positive number") as Any)
        )

        val credentials = credentialResolver.resolveBraintree(merchantId, resolvedBranchId)
            ?: return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(BraintreeErrorResponse("Payment provider not configured") as Any)
            )

        val nonce = request.nonce
        if (nonce.isNullOrBlank()) {
            return Mono.just(
                ResponseEntity.badRequest().body(BraintreeErrorResponse("nonce is required") as Any)
            )
        }

        val currency = request.currency ?: "EUR"
        return braintreePaymentService.checkout(nonce, resolvedAmount, currency, credentials, merchantId, resolvedBranchId, request.productId, request.invoiceId)
            .map { result ->
                when (result) {
                    is BraintreeCheckoutResult.Success -> ResponseEntity.ok(
                        CheckoutSuccessResponse(
                            transactionId = result.transactionId,
                            amount = result.amount,
                            currency = result.currency,
                            invoiceId = result.invoiceId,
                            merchantId = result.merchantId,
                            branchId = result.branchId,
                        ) as Any
                    )
                    is BraintreeCheckoutResult.Failure -> ResponseEntity.ok(
                        CheckoutFailureResponse(message = result.message, code = result.code) as Any
                    )
                }
            }
            .onErrorResume(IllegalArgumentException::class.java) { ex ->
                Mono.just(ResponseEntity.badRequest().body(BraintreeErrorResponse(ex.message ?: "bad request") as Any))
            }
            .onErrorResume { ex ->
                Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(BraintreeErrorResponse(ex.message ?: "Braintree sale failed") as Any)
                )
            }
    }

    private fun resolveAmount(request: CheckoutRequest): Double? {
        val productPrice = request.productId?.let { credentialResolver.resolveProductPrice(it) }
        val effectiveAmount = when {
            request.amount != null && productPrice != null -> request.amount
            request.amount != null -> request.amount
            productPrice != null -> productPrice.toDouble()
            else -> null
        }
        return effectiveAmount?.takeIf { it > 0 && it.isFinite() }
    }
}
