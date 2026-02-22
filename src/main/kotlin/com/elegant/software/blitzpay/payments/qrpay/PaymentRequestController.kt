package com.elegant.software.blitzpay.payments.qrpay

import com.elegant.software.blitzpay.payments.support.PaymentUpdateBus
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentRequested
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentResult
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@Tag(name = "QR Payments", description = "Operations related to QR payments")
@RestController
@RequestMapping("/v1/payments", version = "1")

class PaymentRequestController(private val eventPublisher: ApplicationEventPublisher,
    private val paymentUpdateBus: PaymentUpdateBus) {
    @PostMapping("/request")
    fun createPaymentRequest(@RequestBody request: PaymentRequested): ResponseEntity<Map<String, String>> {
        val paymentRequestId=UUID.randomUUID()
        request.paymentRequestId=paymentRequestId
        eventPublisher.publishEvent(request)
        paymentUpdateBus.emit(paymentRequestId, PaymentResult(paymentRequestId = paymentRequestId,orderId =request.orderId))
        return ResponseEntity.accepted().body(mapOf("paymentRequestId" to paymentRequestId.toString()))
    }



}