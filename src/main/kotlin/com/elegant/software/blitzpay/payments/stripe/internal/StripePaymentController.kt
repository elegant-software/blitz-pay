package com.elegant.software.blitzpay.payments.stripe.internal

import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.stripe.exception.StripeException
import io.swagger.v3.oas.annotations.Operation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

data class CreateIntentRequest(
    val amount: Double? = null,
    val currency: String? = null,
    val merchantId: UUID? = null,
    val branchId: UUID? = null,
    val productId: UUID? = null,
)

data class CreateIntentResponse(val paymentIntent: String, val publishableKey: String)
data class ErrorResponse(val error: String)

@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/payments/stripe", version = "1")
class StripePaymentController(
    private val stripePaymentService: StripePaymentService,
    private val credentialResolver: MerchantCredentialResolver,
) {
    private val log = LoggerFactory.getLogger(StripePaymentController::class.java)

    @Operation(summary = "Create a Stripe PaymentIntent and return credentials to the mobile SDK.")
    @PostMapping("/create-intent")
    fun createIntent(@RequestBody request: CreateIntentRequest): Mono<ResponseEntity<Any>> {
        val merchantId = request.merchantId
            ?: return Mono.just(
                ResponseEntity.badRequest().body(ErrorResponse("merchantId is required") as Any)
            )

        // Resolve branch: explicit branchId → product.merchantBranchId → 400
        val resolvedBranchId = credentialResolver.resolveBranch(merchantId, request.branchId, request.productId)
            ?: return Mono.just(
                ResponseEntity.badRequest().body(ErrorResponse("branch cannot be resolved: supply branchId or productId") as Any)
            )

        // Resolve amount: explicit amount overrides product price; log WARN on mismatch
        val resolvedAmount = resolveAmount(request) ?: return Mono.just(
            ResponseEntity.badRequest().body(ErrorResponse("amount must be a positive number") as Any)
        )

        // Resolve Stripe credentials
        val credentials = credentialResolver.resolveStripe(merchantId, resolvedBranchId)
            ?: return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErrorResponse("Payment provider not configured") as Any)
            )

        val currency = request.currency ?: "eur"
        return stripePaymentService.createIntent(resolvedAmount, currency, credentials, merchantId, resolvedBranchId, request.productId)
            .map { result ->
                ResponseEntity.ok(CreateIntentResponse(result.clientSecret, result.publishableKey) as Any)
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

    private fun resolveAmount(request: CreateIntentRequest): Double? {
        val productPrice = request.productId?.let { credentialResolver.resolveProductPrice(it) }
        val effectiveAmount = when {
            request.amount != null && productPrice != null -> {
                val requestedDouble = request.amount
                val priceDouble = productPrice.toDouble()
                if (requestedDouble != priceDouble) {
                    log.warn(
                        "stripe create_intent amount override productId={} requestAmount={} productPrice={}",
                        request.productId, requestedDouble, priceDouble,
                    )
                }
                requestedDouble
            }
            request.amount != null -> request.amount
            productPrice != null -> productPrice.toDouble()
            else -> null
        }
        return effectiveAmount?.takeIf { it > 0 && it.isFinite() }
    }
}
