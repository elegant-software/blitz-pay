package com.elegant.software.quickpay.support

import com.elegant.software.quickpay.truelayer.api.PaymentResult
import com.elegant.software.quickpay.truelayer.api.QrPaymentStatusUpdate
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class PaymentUpdateBus {
    // For TrueLayer payment results
    private val paymentSinks = ConcurrentHashMap<UUID, Sinks.Many<PaymentResult>>()

    // For QR payment status updates
    private val qrStatusSinks = ConcurrentHashMap<UUID, Sinks.Many<QrPaymentStatusUpdate>>()

    // TrueLayer payment streams
    fun paymentSink(paymentRequestId: UUID): Sinks.Many<PaymentResult> =
        paymentSinks.computeIfAbsent(paymentRequestId) {
            Sinks.many().multicast().onBackpressureBuffer()
        }

    // QR payment status streams
    fun qrStatusSink(paymentRequestId: UUID): Sinks.Many<QrPaymentStatusUpdate> =
        qrStatusSinks.computeIfAbsent(paymentRequestId) {
            Sinks.many().multicast().onBackpressureBuffer()
        }

    // Emit TrueLayer payment result
    fun emitPayment(paymentRequestId: UUID, update: PaymentResult) {
        paymentSink(paymentRequestId).tryEmitNext(update)
    }

    // Emit QR payment status update
    fun emitQrStatus(paymentRequestId: UUID, update: QrPaymentStatusUpdate) {
        qrStatusSink(paymentRequestId).tryEmitNext(update)

        // Also emit as PaymentResult if applicable
        update.paymentResult?.let {
            emitPayment(paymentRequestId, it)
        }
    }

    // Get TrueLayer payment stream
    fun paymentStream(paymentRequestId: UUID): Flux<PaymentResult> =
        paymentSink(paymentRequestId).asFlux()

    // Get QR status stream
    fun qrStatusStream(paymentRequestId: UUID): Flux<QrPaymentStatusUpdate> =
        qrStatusSink(paymentRequestId).asFlux()

    // Complete both streams
    fun complete(paymentRequestId: UUID) {
        paymentSinks.remove(paymentRequestId)?.tryEmitComplete()
        qrStatusSinks.remove(paymentRequestId)?.tryEmitComplete()
    }

    // Legacy method for backward compatibility
    fun sink(paymentRequestId: UUID): Sinks.Many<PaymentResult> = paymentSink(paymentRequestId)

    fun emit(paymentRequestId: UUID, update: PaymentResult) = emitPayment(paymentRequestId, update)
}
