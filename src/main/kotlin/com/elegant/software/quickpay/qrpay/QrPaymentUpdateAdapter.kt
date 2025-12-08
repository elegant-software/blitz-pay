package com.elegant.software.quickpay.qrpay

import com.elegant.software.quickpay.support.PaymentUpdateBus
import com.elegant.software.quickpay.truelayer.api.PaymentResult
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

@Component
class QrPaymentUpdateAdapter(
    private val paymentUpdateBus: PaymentUpdateBus
) {

    fun publishQrInitiated(
        paymentRequestId: UUID,
        qrCodeData: String,
        deepLink: String,
        amount: String,
        currency: String
    ) {
        val paymentResult = PaymentResult(
            paymentRequestId = paymentRequestId,
            status = "qr_initiated",
            qrCodeData = qrCodeData,
            timestamp = Instant.now()
        )
        paymentUpdateBus.emit(paymentRequestId, paymentResult)
    }

    fun publishQrScanned(paymentRequestId: UUID) {
        val paymentResult = PaymentResult(
            paymentRequestId = paymentRequestId,
            status = "qr_scanned",
            timestamp = Instant.now()
        )
        paymentUpdateBus.emit(paymentRequestId, paymentResult)
    }

    fun publishQrExpired(paymentRequestId: UUID) {
        val paymentResult = PaymentResult(
            paymentRequestId = paymentRequestId,
            status = "qr_expired",
            timestamp = Instant.now()
        )
        paymentUpdateBus.emit(paymentRequestId, paymentResult)
    }

    fun publishPaymentStatus(
        paymentRequestId: UUID,
        status: String,
        transactionId: String? = null,
        amount: Double? = null,
        currency: String? = null
    ) {
        val paymentResult = PaymentResult(
            paymentRequestId = paymentRequestId,
            status = status,
            transactionId = transactionId,
            amount = amount,
            currency = currency,
            timestamp = Instant.now()
        )
        paymentUpdateBus.emit(paymentRequestId, paymentResult)
    }
}