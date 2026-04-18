package com.elegant.software.blitzpay.payments.braintree.internal

import com.braintreegateway.BraintreeGateway
import com.braintreegateway.ClientTokenRequest
import com.braintreegateway.TransactionRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.math.BigDecimal
import java.util.Locale
import java.util.Optional

sealed class BraintreeCheckoutResult {
    data class Success(
        val transactionId: String,
        val amount: String,
        val currency: String,
        val invoiceId: String? = null,
    ) : BraintreeCheckoutResult()

    data class Failure(val message: String, val code: String?) : BraintreeCheckoutResult()
}

@Service
class BraintreePaymentService(private val gateway: Optional<BraintreeGateway>) {

    private val log = LoggerFactory.getLogger(BraintreePaymentService::class.java)

    fun isConfigured(): Boolean = gateway.isPresent

    fun generateClientToken(): Mono<String> =
        Mono.fromCallable<String> {
            check(gateway.isPresent) { "Braintree is not configured" }
            val token = gateway.get().clientToken().generate(ClientTokenRequest())
            log.info("braintree client_token issued")
            token
        }.subscribeOn(Schedulers.boundedElastic())

    fun checkout(nonce: String, amount: Double, currency: String, invoiceId: String? = null): Mono<BraintreeCheckoutResult> =
        Mono.fromCallable<BraintreeCheckoutResult> {
            require(nonce.isNotBlank()) { "nonce is required" }
            require(amount > 0 && amount.isFinite()) { "amount must be a positive number" }
            check(gateway.isPresent) { "Braintree is not configured" }
            val formattedAmount = "%.2f".format(Locale.US, amount)
            val request = TransactionRequest()
                .amount(BigDecimal(formattedAmount))
                .paymentMethodNonce(nonce)
                .options().submitForSettlement(true).done()
            val result = gateway.get().transaction().sale(request)
            if (result.isSuccess && result.target != null) {
                val tx = result.target
                log.info("braintree checkout OK tx={} amount={} currency={} invoice={}",
                    tx.id, formattedAmount, currency, invoiceId ?: "n/a")
                BraintreeCheckoutResult.Success(tx.id, formattedAmount, currency, invoiceId)
            } else {
                val tx = result.transaction
                val msg = tx?.processorResponseText ?: result.message ?: "Braintree declined the transaction"
                val code = tx?.processorResponseCode
                log.warn("braintree checkout FAILED code={} message={} invoice={}", code ?: "n/a", msg, invoiceId ?: "n/a")
                BraintreeCheckoutResult.Failure(msg, code)
            }
        }.subscribeOn(Schedulers.boundedElastic())
}
