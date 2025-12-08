package com.elegant.software.quickpay.truelayer.api

import java.time.Instant
import java.util.*

data class QrPaymentRequest(
    val merchant: String,
    val amount: Double,
    val currency: String = "EUR",
    val orderDetails: String,
    val paymentRequestId: UUID = UUID.randomUUID(),
    val createdAt: Instant = Instant.now()
)
data class QrPaymentResponse(
    val success: Boolean = true,
    val paymentRequestId: UUID,
    val transactionId: String,
    val status: QrPaymentStatus,
    val qrCodeData: String? = null,
    val qrCodeImage: String? = null,
    val qrCodeUrl: String? = null,
    val deepLink: String? = null,
    val paymentUrl: String? = null,
    val merchant: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val expiresAt: Instant,
    val message: String? = null,
    val error: String? = null
)

data class QrPaymentStatusUpdate(
    val paymentRequestId: UUID,
    val status: QrPaymentStatus,
    val qrCodeData: String? = null,
    val paymentResult: PaymentResult? = null,
    val timestamp: Instant = Instant.now()
)

enum class QrPaymentStatus {
    INITIATED,          // QR code generated
    PENDING,            // Waiting for payment
    SCANNED,            // QR code scanned by user
    PROCESSING,         // Payment being processed
    SUCCESS,            // Payment successful
    FAILED,             // Payment failed
    EXPIRED,            // QR code expired
    CANCELLED           // Payment cancelled
}