package com.elegant.software.blitzpay.payments.qrpay

import com.elegant.software.blitzpay.payments.support.PaymentUpdateBus
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentResult
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/qr-payments", version = "1")
class QrPaymentSseController(private val bus: PaymentUpdateBus) {
    private val logger = LoggerFactory.getLogger(QrPaymentSseController::class.java)

    @GetMapping(
        value = ["/{paymentRequestId}/events"],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    fun stream(@PathVariable paymentRequestId: UUID): Flux<ServerSentEvent<PaymentResult>> {
        logger.info("SSE client connected for paymentRequestId: {}", paymentRequestId)
        return bus.sink(paymentRequestId)
            .asFlux()
            .timeout(Duration.ofMinutes(5), Flux.empty())     // auto-close idle streams gracefully
            .map { update ->
                ServerSentEvent.builder(update)
                    .event("payment")
                    .id(update.paymentRequestId.toString())
                    .build()
            }
            .doFinally { signal -> 
                logger.debug("SSE stream ended with signal {} for paymentRequestId: {}", signal, paymentRequestId)
                bus.complete(paymentRequestId) 
            }
    }
}
