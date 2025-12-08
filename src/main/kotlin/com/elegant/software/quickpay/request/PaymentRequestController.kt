package com.elegant.software.quickpay.request  // Changed from qrpay to request

import com.elegant.software.quickpay.support.PaymentUpdateBus
import com.elegant.software.quickpay.truelayer.api.PaymentRequested
import com.elegant.software.quickpay.truelayer.api.PaymentResult
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/payments")
class PaymentRequestController(
    private val eventPublisher: ApplicationEventPublisher,
    private val paymentUpdateBus: PaymentUpdateBus
) {
    @PostMapping("/request")
    fun createPaymentRequest(@RequestBody request: PaymentRequested): ResponseEntity<Map<String, String>> {
        val paymentRequestId = UUID.randomUUID()
        request.paymentRequestId = paymentRequestId

        // Publish event for further processing
        eventPublisher.publishEvent(request)

        // Create PaymentResult for SSE (no QR code for regular payments)
        val paymentResult = PaymentResult(
            paymentRequestId = paymentRequestId,
            status = "requested",  // Initial status
            // For regular payments, we don't have transactionId until later
            // For QR payments, these would be set
            qrCodeData = null,     // No QR code for regular payments
            qrStatus = null,       // Not a QR payment
            deepLink = null,       // No deep link for regular payments
            timestamp = Instant.now()
        )

        // Emit to SSE bus
        paymentUpdateBus.emit(paymentRequestId, paymentResult)

        return ResponseEntity.accepted()
            .body(mapOf(
                "paymentRequestId" to paymentRequestId.toString(),
                "status" to "requested",
                "message" to "Payment request created successfully"
            ))
    }
}