package com.elegant.software.quickpay.payments.qrpay

import com.elegant.software.quickpay.payments.support.PaymentUpdateBus
import com.elegant.software.quickpay.payments.truelayer.api.PaymentResult
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.SignalType
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/qr-payments")
class QrPaymentSseController(private val bus: PaymentUpdateBus) {
    private val logger = KotlinLogging.logger {}

    @GetMapping(
        value = ["/{paymentRequestId}/events"],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    fun stream(@PathVariable paymentRequestId: UUID): Flux<ServerSentEvent<PaymentResult>> {
        logger.info { "SSE client connected for paymentRequestId: $paymentRequestId" }
        return bus.sink(paymentRequestId)
            .asFlux()
            .timeout(Duration.ofMinutes(5), Flux.empty())     // auto-close idle streams gracefully
            .map { update ->
                ServerSentEvent.builder(update)
                    .event("payment")
                    .id(update.paymentRequestId.toString())
                    .build()
            }
            .doFinally { signal -> if (signal == SignalType.ON_COMPLETE) bus.complete(paymentRequestId) }
    }
}
