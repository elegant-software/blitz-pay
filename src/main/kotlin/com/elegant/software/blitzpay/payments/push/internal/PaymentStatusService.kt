package com.elegant.software.blitzpay.payments.push.internal

import com.elegant.software.blitzpay.payments.push.api.PaymentStatusCode
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusResponse
import com.elegant.software.blitzpay.payments.push.persistence.PaymentStatusEntity
import com.elegant.software.blitzpay.payments.push.persistence.PaymentStatusRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional
import java.util.UUID

data class StatusTransition(
    val paymentRequestId: UUID,
    val newStatus: PaymentStatusCode,
    val previousStatus: PaymentStatusCode?,
    val occurredAt: Instant,
    val sourceEventId: String,
    val changed: Boolean,
)

@Service
class PaymentStatusService(
    private val repository: PaymentStatusRepository,
) {
    private val log = LoggerFactory.getLogger(PaymentStatusService::class.java)

    fun getByRequestId(paymentRequestId: UUID): Optional<PaymentStatusResponse> =
        repository.findById(paymentRequestId).map { entity ->
            PaymentStatusResponse(
                paymentRequestId = entity.paymentRequestId,
                status = entity.currentStatus,
                terminal = entity.currentStatus.isTerminal(),
                lastEventAt = entity.lastEventAt,
            )
        }

    @Transactional
    fun apply(
        paymentRequestId: UUID,
        newStatus: PaymentStatusCode,
        occurredAt: Instant,
        sourceEventId: String,
    ): StatusTransition {
        val existing = repository.findById(paymentRequestId).orElse(null)
        if (existing == null) {
            val saved = repository.save(
                PaymentStatusEntity(
                    paymentRequestId = paymentRequestId,
                    currentStatus = newStatus,
                    lastEventId = sourceEventId,
                    lastEventAt = occurredAt,
                    updatedAt = Instant.now(),
                )
            )
            log.info("payment status created request={} status={}", paymentRequestId, saved.currentStatus)
            return StatusTransition(paymentRequestId, newStatus, null, occurredAt, sourceEventId, changed = true)
        }

        val previous = existing.currentStatus
        if (newStatus.rank() < previous.rank() || (newStatus == previous)) {
            log.info("payment status unchanged request={} current={} incoming={}", paymentRequestId, previous, newStatus)
            return StatusTransition(paymentRequestId, previous, previous, occurredAt, sourceEventId, changed = false)
        }

        existing.currentStatus = newStatus
        existing.lastEventId = sourceEventId
        existing.lastEventAt = occurredAt
        existing.updatedAt = Instant.now()
        repository.save(existing)
        log.info("payment status transition request={} {}->{}", paymentRequestId, previous, newStatus)
        return StatusTransition(paymentRequestId, newStatus, previous, occurredAt, sourceEventId, changed = true)
    }
}
