package com.elegant.software.blitzpay.payments.braintree.internal

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusUpdateGateway
import io.swagger.v3.oas.annotations.Operation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Instant
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
    val orderId: String,
    val invoiceId: String? = null,
    val merchantId: UUID? = null,
    val branchId: UUID? = null,
    val productId: UUID? = null,
)
data class CheckoutSuccessResponse(
    val status: String = "succeeded",
    val paymentRequestId: UUID,
    val transactionId: String,
    val amount: String,
    val currency: String,
    val orderId: String,
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
    private val orderGateway: OrderGateway,
    private val paymentStatusInitializationGateway: PaymentStatusInitializationGateway,
    private val paymentStatusUpdateGateway: PaymentStatusUpdateGateway,
) {
    private val log = LoggerFactory.getLogger(BraintreePaymentController::class.java)

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

        return LogContext.with(
            LogContext.PROVIDER to "BRAINTREE",
        ) {
            log.info("braintree client-token request merchantId={} branchId={}", merchantId, resolvedBranchId)
            braintreePaymentService.generateClientToken(credentials)
                .map { token ->
                    log.info("braintree client-token generated tokenPresent={}", token.isNotBlank())
                    ResponseEntity.ok(ClientTokenResponse(token) as Any)
                }
                .onErrorResume { ex ->
                    log.error("braintree client-token failed message={}", ex.message, ex)
                    Mono.just(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BraintreeErrorResponse(ex.message ?: "Braintree client token generation failed") as Any)
                    )
                }
        }
    }

    @Operation(summary = "Submit a Braintree payment nonce for settlement.")
    @PostMapping("/checkout")
    fun checkout(@RequestBody request: CheckoutRequest): Mono<ResponseEntity<Any>> {
        val merchantId = request.merchantId
            ?: return Mono.just(
                ResponseEntity.badRequest().body(BraintreeErrorResponse("merchantId is required") as Any)
            )
        val orderId = request.orderId.trim().takeIf { it.isNotEmpty() }
            ?: return Mono.just(
                ResponseEntity.badRequest().body(BraintreeErrorResponse("orderId must not be blank") as Any)
            )
        val orderSummary = try {
            orderGateway.assertPayable(orderId)
        } catch (ex: NoSuchElementException) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(BraintreeErrorResponse(ex.message ?: "Order not found") as Any))
        } catch (ex: IllegalStateException) {
            return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(BraintreeErrorResponse(ex.message ?: "Order is not payable") as Any))
        }
        if (merchantId != orderSummary.merchantId) {
            return Mono.just(
                ResponseEntity.badRequest().body(BraintreeErrorResponse("merchantId does not match the order") as Any)
            )
        }

        val resolvedBranchId = credentialResolver.resolveBranch(merchantId, request.branchId ?: orderSummary.branchId, request.productId)
            ?: return Mono.just(
                ResponseEntity.badRequest()
                    .body(BraintreeErrorResponse("branch cannot be resolved: supply branchId or productId") as Any)
            )

        val resolvedAmount = resolveAmount(request, orderSummary.totalAmountMinor) ?: return Mono.just(
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
        val paymentRequestId = UUID.randomUUID()
        return LogContext.with(
            LogContext.ORDER_ID to orderId,
            LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
            LogContext.PROVIDER to "BRAINTREE",
        ) {
            log.info(
                "braintree checkout request merchantId={} branchId={} amount={} currency={} invoiceId={} productId={}",
                merchantId, resolvedBranchId, resolvedAmount, currency.uppercase(), request.invoiceId, request.productId,
            )
            paymentStatusInitializationGateway.initialize(
                paymentRequestId = paymentRequestId,
                payerRef = null,
                orderId = orderId,
                amountMinorUnits = Math.round(resolvedAmount * 100),
                currency = currency.uppercase(),
            )
            log.info("braintree payment status initialized")
            orderGateway.linkPaymentAttempt(orderId, paymentRequestId, "BRAINTREE", null)
            log.info("braintree payment attempt linked to order")
            braintreePaymentService.checkout(
                nonce,
                resolvedAmount,
                currency,
                credentials,
                merchantId,
                resolvedBranchId,
                orderId,
                request.productId,
                request.invoiceId
            )
                .map { result ->
                    when (result) {
                        is BraintreeCheckoutResult.Success -> {
                            log.info(
                                "braintree checkout succeeded transactionId={} amount={} currency={}",
                                result.transactionId, result.amount, result.currency,
                            )
                            orderGateway.linkPaymentAttempt(orderId, paymentRequestId, "BRAINTREE", result.transactionId)
                            paymentStatusUpdateGateway.settle(paymentRequestId, "braintree:${result.transactionId}", Instant.now())
                            ResponseEntity.ok(
                                CheckoutSuccessResponse(
                                    paymentRequestId = paymentRequestId,
                                    transactionId = result.transactionId,
                                    amount = result.amount,
                                    currency = result.currency,
                                    orderId = result.orderId,
                                    invoiceId = result.invoiceId,
                                    merchantId = result.merchantId,
                                    branchId = result.branchId,
                                ) as Any
                            )
                        }
                        is BraintreeCheckoutResult.Failure -> {
                            log.warn("braintree checkout failed code={} message={}", result.code, result.message)
                            paymentStatusUpdateGateway.fail(paymentRequestId, "braintree:fail:$paymentRequestId", Instant.now())
                            ResponseEntity.ok(CheckoutFailureResponse(message = result.message, code = result.code) as Any)
                        }
                    }
                }
                .onErrorResume(IllegalArgumentException::class.java) { ex ->
                    log.warn("braintree checkout rejected error={}", ex.message)
                    Mono.just(ResponseEntity.badRequest().body(BraintreeErrorResponse(ex.message ?: "bad request") as Any))
                }
                .onErrorResume { ex ->
                    log.error("braintree checkout failed message={}", ex.message, ex)
                    Mono.just(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BraintreeErrorResponse(ex.message ?: "Braintree sale failed") as Any)
                    )
                }
        }
    }

    private fun resolveAmount(request: CheckoutRequest, orderAmountMinor: Long): Double? {
        val productPrice = request.productId?.let { credentialResolver.resolveProductPrice(it) }
        val effectiveAmount = when {
            request.amount != null && productPrice != null -> request.amount
            request.amount != null -> request.amount
            productPrice != null -> productPrice.toDouble()
            else -> orderAmountMinor / 100.0
        }
        return effectiveAmount.takeIf { it > 0 && it.isFinite() }
    }
}
