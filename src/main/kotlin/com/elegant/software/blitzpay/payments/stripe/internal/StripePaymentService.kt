package com.elegant.software.blitzpay.payments.stripe.internal

import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.stripe.exception.StripeException
import com.stripe.model.PaymentIntent
import com.stripe.param.PaymentIntentCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

data class StripeIntentResult(val clientSecret: String, val publishableKey: String)

@Service
class StripePaymentService(private val properties: StripeProperties) {

    private val log = LoggerFactory.getLogger(StripePaymentService::class.java)

    fun createIntent(amount: Double, currency: String): Mono<StripeIntentResult> =
        Mono.fromCallable<StripeIntentResult> {
            require(amount > 0 && amount.isFinite()) { "amount must be a positive number" }
            val amountInSmallestUnit = Math.round(amount * 100)
            val params = PaymentIntentCreateParams.builder()
                .setAmount(amountInSmallestUnit)
                .setCurrency(currency.lowercase())
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                )
                .build()
            try {
                val intent = PaymentIntent.create(params)
                log.info("stripe create_intent id={} amount={} currency={}", intent.id, amount, currency.lowercase())
                StripeIntentResult(
                    clientSecret = requireNotNull(intent.clientSecret) { "Stripe returned null clientSecret" },
                    publishableKey = properties.publishableKey,
                )
            } catch (ex: StripeException) {
                log.error("stripe create_intent FAILED amount={} currency={} code={} message={}",
                    amount, currency, ex.code, ex.message)
                throw ex
            }
        }.subscribeOn(Schedulers.boundedElastic())
}
