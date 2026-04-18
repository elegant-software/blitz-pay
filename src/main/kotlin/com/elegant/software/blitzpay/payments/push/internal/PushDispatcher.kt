package com.elegant.software.blitzpay.payments.push.internal

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusChanged
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusCode
import com.elegant.software.blitzpay.payments.push.persistence.DeliveryOutcome
import com.elegant.software.blitzpay.payments.push.persistence.DeviceRegistrationRepository
import com.elegant.software.blitzpay.payments.push.persistence.PushDeliveryAttemptEntity
import com.elegant.software.blitzpay.payments.push.persistence.PushDeliveryAttemptRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class PushDispatcher(
    private val deviceRepository: DeviceRegistrationRepository,
    private val pushClient: ExpoPushClient,
    private val attemptRepository: PushDeliveryAttemptRepository,
) {
    private val log = LoggerFactory.getLogger(PushDispatcher::class.java)

    fun dispatch(event: PaymentStatusChanged) = LogContext.with(
        LogContext.PAYMENT_REQUEST_ID to event.paymentRequestId,
        LogContext.EVENT_ID to event.sourceEventId,
    ) {
        val devices = deviceRepository.findByPaymentRequestIdAndInvalidFalse(event.paymentRequestId)
        if (devices.isEmpty()) {
            log.info("push dispatch skipped reason=no_devices status={}", event.newStatus)
            return@with
        }

        val (title, body) = messageFor(event.newStatus)
        val messages = devices.map { device ->
            ExpoMessage(
                to = device.expoPushToken,
                title = title,
                body = body,
                data = mapOf(
                    "paymentRequestId" to event.paymentRequestId.toString(),
                    "status" to event.newStatus.name,
                ),
            )
        }

        log.info("push dispatch start status={} deviceCount={}", event.newStatus, devices.size)

        val tickets = try {
            pushClient.send(messages)
        } catch (ex: Exception) {
            log.error("push dispatch failed status={} deviceCount={} errorClass={}", event.newStatus, devices.size, ex.javaClass.simpleName, ex)
            return@with
        }

        val accepted = tickets.count { it.status == ExpoTicket.Status.OK }
        val rejected = tickets.size - accepted
        log.info("push dispatch complete status={} accepted={} rejected={}", event.newStatus, accepted, rejected)

        tickets.forEach { ticket ->
            if (ticket.status != ExpoTicket.Status.OK) {
                log.warn("push ticket rejected token={} errorCode={} ticketId={}", maskToken(ticket.token), ticket.errorCode, ticket.ticketId)
            }
            try {
                val outcome = if (ticket.status == ExpoTicket.Status.OK) DeliveryOutcome.ACCEPTED else DeliveryOutcome.REJECTED
                attemptRepository.save(
                    PushDeliveryAttemptEntity(
                        id = UUID.randomUUID(),
                        paymentRequestId = event.paymentRequestId,
                        expoPushToken = ticket.token,
                        statusCode = event.newStatus,
                        ticketId = ticket.ticketId,
                        outcome = outcome,
                        errorCode = ticket.errorCode,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    )
                )
            } catch (ex: Exception) {
                log.warn("push delivery attempt persist failed token={} errorClass={}", maskToken(ticket.token), ex.javaClass.simpleName, ex)
            }
        }
    }

    private fun maskToken(token: String): String =
        if (token.length <= 12) "***" else "${token.take(8)}…${token.takeLast(4)}"

    private fun messageFor(status: PaymentStatusCode): Pair<String, String> = when (status) {
        PaymentStatusCode.EXECUTED -> "Payment executed" to "Your payment was executed and is clearing."
        PaymentStatusCode.SETTLED -> "Payment settled" to "Your payment has settled successfully."
        PaymentStatusCode.FAILED -> "Payment failed" to "Your payment could not be completed."
        PaymentStatusCode.EXPIRED -> "Payment expired" to "Your payment request has expired."
        PaymentStatusCode.PENDING -> "Payment pending" to "Your payment is being processed."
    }
}
