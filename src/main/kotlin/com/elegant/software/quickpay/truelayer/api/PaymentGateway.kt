package com.elegant.software.quickpay.truelayer.api

import org.springframework.modulith.NamedInterface
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@NamedInterface("PaymentGateway")

data class PaymentRequested(
    var paymentRequestId: UUID? = null,
    val orderId: String?,
    val amount: BigDecimal,
    val currency: String = "EUR",
    val merchant: String?,
    val userDisplayName: String? = null,
    val redirectReturnUri: String? = null,
    val customerName: String? = null,
    val customerEmail: String? = null,
    val createdAt: Instant = Instant.now()
)

// Extend existing PaymentResult or create a wrapper
data class PaymentResult(
    val paymentRequestId: UUID,
    val status: String, // "executed", "settled", "failed", etc.
    val transactionId: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val errorMessage: String? = null,
    val timestamp: Instant = Instant.now(),

    // QR-specific fields (nullable for non-QR payments)
    val qrCodeData: String? = null,
    val qrStatus: String? = null, // "INITIATED", "SCANNED", etc.
    val deepLink: String? = null
)