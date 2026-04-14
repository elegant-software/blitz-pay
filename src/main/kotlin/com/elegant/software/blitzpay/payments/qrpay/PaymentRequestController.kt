package com.elegant.software.blitzpay.payments.qrpay

import com.elegant.software.blitzpay.payments.support.PaymentUpdateBus
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentGateway
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentRequested
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@Tag(name = "QR Payments", description = "Operations related to QR payments")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/payments", version = "1")

class PaymentRequestController(
    private val paymentGateway: PaymentGateway,
    private val paymentUpdateBus: PaymentUpdateBus
) {
    @PostMapping("/request")
    fun createPaymentRequest(@RequestBody request: PaymentRequested): ResponseEntity<Map<String, String?>> {
        val paymentRequestId = UUID.randomUUID()
        request.paymentRequestId = paymentRequestId

        val result = paymentGateway.startPayment(request)
        paymentUpdateBus.emit(paymentRequestId, result)

        return ResponseEntity.accepted().body(
            mapOf(
                "paymentRequestId" to paymentRequestId.toString(),
                "paymentId" to result.paymentId,
                "resourceToken" to result.resourceToken,
                "redirectReturnUri" to (result.redirectReturnUri ?: request.redirectReturnUri)
            )
        )
    }
}
