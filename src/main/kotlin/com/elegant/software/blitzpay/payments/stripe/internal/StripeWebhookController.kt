package com.elegant.software.blitzpay.payments.stripe.internal

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusUpdateGateway
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.stripe.exception.EventDataObjectDeserializationException
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.PaymentIntent
import com.stripe.net.Webhook
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/webhooks/stripe", version = "1")
class StripeWebhookController(
    private val stripeProperties: StripeProperties,
    private val paymentStatusUpdateGateway: PaymentStatusUpdateGateway,
) {
    private val log = LoggerFactory.getLogger(StripeWebhookController::class.java)

    @PostMapping
    fun receive(
        @RequestHeader("Stripe-Signature") signature: String?,
        @RequestBody payload: String,
    ): ResponseEntity<Any> {
        return LogContext.with(LogContext.PROVIDER to "STRIPE") {
            log.info("stripe webhook received payloadLength={} signaturePresent={}", payload.length, !signature.isNullOrBlank())
            if (stripeProperties.webhookSecret.isBlank()) {
                log.warn("stripe webhook received but STRIPE_WEBHOOK_SECRET is not configured")
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
            }
            if (signature.isNullOrBlank()) {
                log.warn("stripe webhook rejected reason=missing_signature")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }

            val event = try {
                Webhook.constructEvent(payload, signature, stripeProperties.webhookSecret)
            } catch (ex: SignatureVerificationException) {
                log.warn("stripe webhook rejected reason=signature_invalid error={}", ex.message)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }

            LogContext.with(LogContext.EVENT_ID to event.id) {
                log.info("stripe webhook verified type={} created={}", event.type, event.created)
                val paymentIntent = extractPaymentIntent(event)
                    ?: run {
                        log.warn(
                            "stripe webhook event={} type={} could not deserialize PaymentIntent; ignoring and skipping payment status update",
                            event.id,
                            event.type,
                        )
                        return ResponseEntity.ok().build()
                    }

                val paymentRequestId = paymentIntent.metadata["paymentRequestId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run {
                        log.warn("stripe webhook event={} missing paymentRequestId in metadata intentId={}", event.id, paymentIntent.id)
                        return ResponseEntity.ok().build()
                    }

                return LogContext.with(
                    LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
                    LogContext.PAYMENT_INTENT_ID to paymentIntent.id,
                ) {
                    val occurredAt = Instant.ofEpochSecond(event.created)
                    log.info(
                        "stripe webhook mapped paymentRequestId metadataKeys={} occurredAt={}",
                        paymentIntent.metadata.keys, occurredAt,
                    )

                    when (event.type) {
                        "payment_intent.succeeded" -> {
                            log.info("stripe webhook settling payment")
                            paymentStatusUpdateGateway.settle(paymentRequestId, "stripe:${event.id}", occurredAt)
                        }
                        "payment_intent.payment_failed" -> {
                            log.info("stripe webhook failing payment")
                            paymentStatusUpdateGateway.fail(paymentRequestId, "stripe:${event.id}", occurredAt)
                        }
                        else -> log.debug("stripe webhook event={} type={} not handled", event.id, event.type)
                    }

                    ResponseEntity.ok().build()
                }
            }
        }
    }

    private fun extractPaymentIntent(event: Event): PaymentIntent? {
        val deserializer = event.dataObjectDeserializer
        val typedObject = deserializer.getObject().orElse(null) as? PaymentIntent
        if (typedObject != null) {
            log.info("stripe webhook event={} deserialized PaymentIntent via typed deserializer", event.id)
            return typedObject
        }

        return try {
            val unsafeObject = deserializer.deserializeUnsafe() as? PaymentIntent
            if (unsafeObject != null) {
                log.warn(
                    "stripe webhook event={} deserialized PaymentIntent via unsafe fallback due to SDK/API version mismatch",
                    event.id,
                )
            }
            unsafeObject
        } catch (ex: EventDataObjectDeserializationException) {
            log.warn(
                "stripe webhook event={} failed unsafe PaymentIntent deserialization error={}",
                event.id,
                ex.message,
            )
            null
        }
    }
}
