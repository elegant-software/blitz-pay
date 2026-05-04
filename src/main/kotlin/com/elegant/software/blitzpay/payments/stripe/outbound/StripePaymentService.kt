package com.elegant.software.blitzpay.payments.stripe.internal

import com.elegant.software.blitzpay.merchant.api.StripeCredentials
import com.stripe.exception.StripeException
import com.stripe.model.PaymentIntent
import com.stripe.net.RequestOptions
import com.stripe.param.PaymentIntentCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

data class StripeIntentResult(
    val clientSecret: String,
    val paymentIntentId: String,
    val publishableKey: String,
)

@Service
class StripePaymentService {

    private val log = LoggerFactory.getLogger(StripePaymentService::class.java)

    fun createIntent(
        amount: Double,
        currency: String,
        credentials: StripeCredentials,
        merchantId: UUID,
        branchId: UUID?,
        orderId: String,
        paymentRequestId: UUID,
        productId: UUID?,
    ): Mono<StripeIntentResult> = Mono.fromCallable {
        require(amount > 0 && amount.isFinite()) { "amount must be a positive number" }
        val amountInSmallestUnit = Math.round(amount * 100)
        val params = PaymentIntentCreateParams.builder()
            .setAmount(amountInSmallestUnit)
            .setCurrency(currency.lowercase())
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
            )
            .putMetadata("merchantId", merchantId.toString())
            .putMetadata("orderId", orderId)
            .putMetadata("paymentRequestId", paymentRequestId.toString())
            .apply { branchId?.let { putMetadata("branchId", it.toString()) } }
            .apply { productId?.let { putMetadata("productId", it.toString()) } }
            .build()
        val requestOptions = RequestOptions.builder()
            .setApiKey(credentials.secretKey)
            .build()
        try {
            val intent = PaymentIntent.create(params, requestOptions)
            log.info(
                "stripe create_intent id={} amount={} currency={} merchantId={} branchId={} orderId={} paymentRequestId={} productId={}",
                intent.id, amount, currency.lowercase(), merchantId, branchId, orderId, paymentRequestId, productId,
            )
            StripeIntentResult(
                clientSecret = requireNotNull(intent.clientSecret) { "Stripe returned null clientSecret" },
                paymentIntentId = intent.id,
                publishableKey = credentials.publishableKey,
            )
        } catch (ex: StripeException) {
            log.error(
                "stripe create_intent FAILED amount={} currency={} merchantId={} code={} message={}",
                amount, currency, merchantId, ex.code, ex.message,
            )
            throw ex
        }
    }.subscribeOn(Schedulers.boundedElastic())
}
