package com.elegant.software.blitzpay.payments.push.internal

import TlWebhookEnvelope
import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusChanged
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusCode
import com.elegant.software.blitzpay.payments.push.persistence.ProcessedWebhookEventEntity
import com.elegant.software.blitzpay.payments.push.persistence.ProcessedWebhookEventRepository
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
class TlWebhookEventListener(
    private val processedRepository: ProcessedWebhookEventRepository,
    private val paymentStatusService: PaymentStatusService,
    private val publisher: ApplicationEventPublisher,
) {
    private val log = KotlinLogging.logger {}

    @Transactional
    @EventListener
    fun on(envelope: TlWebhookEnvelope) {
        val eventId = envelope.event_id
        if (eventId.isNullOrBlank()) {
            log.warn { "webhook rejected reason=missing_event_id type=${envelope.type}" }
            return
        }
        LogContext.with(LogContext.EVENT_ID to eventId) {
            handleVerified(envelope, eventId)
        }
    }

    private fun handleVerified(envelope: TlWebhookEnvelope, eventId: String) {
        val newStatus = mapStatus(envelope.type) ?: run {
            log.debug { "webhook skipped reason=unmapped_type type=${envelope.type}" }
            return
        }
        val paymentRequestId = envelope.metadata?.get("paymentRequestId")
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (paymentRequestId == null) {
            log.warn { "webhook rejected reason=missing_payment_request_id_metadata type=${envelope.type}" }
            return
        }

        LogContext.with(LogContext.PAYMENT_REQUEST_ID to paymentRequestId) {
            if (processedRepository.existsById(eventId)) {
                log.info { "webhook skipped reason=duplicate_event" }
                return@with
            }
            processedRepository.save(ProcessedWebhookEventEntity(eventId = eventId))

            val occurredAt = runCatching { envelope.timestamp?.let(Instant::parse) }.getOrNull() ?: Instant.now()
            val transition = paymentStatusService.apply(paymentRequestId, newStatus, occurredAt, eventId)
            if (transition.changed) {
                publisher.publishEvent(
                    PaymentStatusChanged(
                        paymentRequestId = transition.paymentRequestId,
                        newStatus = transition.newStatus,
                        previousStatus = transition.previousStatus,
                        occurredAt = transition.occurredAt,
                        sourceEventId = transition.sourceEventId,
                    )
                )
            }
        }
    }

    private fun mapStatus(type: String): PaymentStatusCode? = when (type) {
        "payment_executed" -> PaymentStatusCode.EXECUTED
        "payment_settled" -> PaymentStatusCode.SETTLED
        "payment_failed" -> PaymentStatusCode.FAILED
        "payment_expired" -> PaymentStatusCode.EXPIRED
        else -> null
    }
}
