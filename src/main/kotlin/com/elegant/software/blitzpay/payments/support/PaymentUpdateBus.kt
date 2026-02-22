package com.elegant.software.blitzpay.payments.support

import com.elegant.software.blitzpay.payments.truelayer.api.PaymentResult
import org.springframework.stereotype.Component
import reactor.core.publisher.Sinks
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class PaymentUpdateBus {
    private val sinks = ConcurrentHashMap<UUID, Sinks.Many<PaymentResult>>()

    fun sink(paymentRequestId: UUID): Sinks.Many<PaymentResult> =
        sinks.computeIfAbsent(paymentRequestId) {
            Sinks.many().multicast().onBackpressureBuffer()
        }

    fun emit(paymentRequestId: UUID, update: PaymentResult) {
        sink(paymentRequestId).tryEmitNext(update)
    }

    fun complete(paymentRequestId: UUID) {
        sinks.remove(paymentRequestId)?.tryEmitComplete()
    }
}
