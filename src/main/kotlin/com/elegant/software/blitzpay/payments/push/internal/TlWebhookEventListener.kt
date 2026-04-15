package com.elegant.software.blitzpay.payments.push.internal

import TlWebhookEnvelope
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
            log.warn { "webhook without event_id; ignoring" }
            return
        }
        val newStatus = mapStatus(envelope.type) ?: run {
            log.debug { "webhook type=${envelope.type} does not map to a status; skipping" }
            return
        }
        val paymentRequestId = envelope.metadata?.get("paymentRequestId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (paymentRequestId == null) {
            log.warn { "webhook missing paymentRequestId metadata event=$eventId" }
            return
        }

        if (processedRepository.existsById(eventId)) {
            log.info { "webhook duplicate event=$eventId; skipping" }
            return
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

    private fun mapStatus(type: String): PaymentStatusCode? = when (type) {
        "payment_executed" -> PaymentStatusCode.EXECUTED
        "payment_settled" -> PaymentStatusCode.SETTLED
        "payment_failed" -> PaymentStatusCode.FAILED
        "payment_expired" -> PaymentStatusCode.EXPIRED
        else -> null
    }
}
