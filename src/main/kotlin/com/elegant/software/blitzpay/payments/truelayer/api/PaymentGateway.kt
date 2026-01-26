package com.elegant.software.blitzpay.payments.truelayer.api
import com.truelayer.java.payments.entities.paymentdetail.Status
import org.springframework.modulith.NamedInterface
import java.net.URI
import java.util.UUID

@NamedInterface("PaymentGateway")

    data class PaymentRequested(
        var paymentRequestId: UUID?,
        val orderId: String,
        val amountMinorUnits: Long,
        val currency: String,
        val userDisplayName: String,
        val redirectReturnUri: String
    )
    data class PaymentResult(
        val paymentRequestId: UUID,
        val orderId: String,
        val paymentId: String? = null,
        val redirectURI: URI? = null,
        val status: Status? = null,
        val error: String? = null
        )