package com.elegant.software.blitzpay.payments.qrpay

import com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway
import com.elegant.software.blitzpay.payments.support.PaymentUpdateBus
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentGateway
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentRequested
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Base64
import java.util.*

@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/payments", version = "1")

class PaymentRequestController(
    private val paymentGateway: PaymentGateway,
    private val paymentUpdateBus: PaymentUpdateBus,
    private val paymentStatusInitializationGateway: PaymentStatusInitializationGateway,
) {
    @PostMapping("/request")
    fun createPaymentRequest(
        @RequestBody request: PaymentRequested,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<Map<String, String?>> {
        val paymentRequestId = UUID.randomUUID()
        request.paymentRequestId = paymentRequestId
        request.payerRef = extractSubject(authorization)

        paymentStatusInitializationGateway.initialize(
            paymentRequestId = paymentRequestId,
            payerRef = request.payerRef,
            orderId = request.orderId,
            amountMinorUnits = request.amountMinorUnits,
            currency = request.currency,
        )

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

    private fun extractSubject(authorization: String?): String? {
        val bearerToken = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val parts = bearerToken.split('.')
        if (parts.size < 2) return null

        return runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]))
        }.mapCatching { payload ->
            Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.getOrNull(1)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(512)
    }
}
