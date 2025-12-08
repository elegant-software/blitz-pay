package com.elegant.software.quickpay.qrpay

import com.elegant.software.quickpay.support.PaymentUpdateBus
import mu.KotlinLogging
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
@RequestMapping("/qr-payments/status")
class QrPaymentStatusSseController(
    private val paymentUpdateBus: PaymentUpdateBus
) {
    private val logger = KotlinLogging.logger {}

    @GetMapping(
        value = ["/{paymentRequestId}/events"],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    fun streamStatus(@PathVariable paymentRequestId: UUID): Flux<ServerSentEvent<String>> {
        logger.info { "QR status SSE connected for paymentRequestId: $paymentRequestId" }

        return paymentUpdateBus.paymentStream(paymentRequestId)
            .map { paymentResult ->
                // Convert PaymentResult to JSON or status message
                val statusMessage = when (paymentResult.status) {
                    "qr_initiated" -> "QR code generated"
                    "qr_scanned" -> "QR code scanned"
                    "qr_expired" -> "QR code expired"
                    "executed" -> "Payment successful"
                    "failed" -> "Payment failed"
                    else -> "Status: ${paymentResult.status}"
                }

                ServerSentEvent.builder<String>()
                    .event("qr_payment_status")
                    .id(paymentResult.paymentRequestId.toString())
                    .data("""
                        {
                            "paymentRequestId": "${paymentResult.paymentRequestId}",
                            "status": "${paymentResult.status}",
                            "message": "$statusMessage",
                            "qrCodeData": "${paymentResult.qrCodeData ?: ""}",
                            "timestamp": "${paymentResult.timestamp}"
                        }
                    """.trimIndent())
                    .build()
            }
            .timeout(Duration.ofMinutes(5), Flux.empty())
            .doFinally { signal ->
                logger.debug { "QR status SSE stream ended: $signal" }
            }
    }
}