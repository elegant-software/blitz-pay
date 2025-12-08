package com.elegant.software.quickpay.truelayer.support

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.*

@Service
class QrCodeGenerator {
    private val logger = KotlinLogging.logger {}

    data class QrCodeResult(
        val paymentUrl: String,     // TrueLayer web URL
        val deepLink: String,       // truelayer:// deep link
        val qrCodeImage: String     // Base64 encoded QR image
    )

    fun generatePaymentQRCode(
        paymentRequestId: UUID,
        amount: String,
        merchant: String,
        currency: String = "EUR",
        paymentUrl: String? = null
    ): QrCodeResult {
        logger.info { "Generating QR code for payment: $paymentRequestId" }

        // Use provided payment URL or generate one
        val finalPaymentUrl = paymentUrl ?: createTrueLayerPaymentUrl(
            paymentRequestId = paymentRequestId,
            amount = amount,
            merchant = merchant,
            currency = currency
        )

        // Generate deep link
        val deepLink = createDeepLink(finalPaymentUrl)

        // Generate QR code image (simplified - in production use a QR library)
        val qrCodeImage = generateQRCodeBase64(finalPaymentUrl)

        return QrCodeResult(
            paymentUrl = finalPaymentUrl,
            deepLink = deepLink,
            qrCodeImage = qrCodeImage
        )
    }

    private fun createTrueLayerPaymentUrl(
        paymentRequestId: UUID,
        amount: String,
        merchant: String,
        currency: String
    ): String {
        // This is now just a fallback - should use the actual payment link from TrueLayer
        return "https://payment.truelayer.com/checkout/$paymentRequestId"
    }

    private fun createDeepLink(paymentUrl: String): String {
        // Extract payment ID from URL
        val paymentId = paymentUrl.substringAfterLast("/")
        return "truelayer://payment/$paymentId"
    }

    fun generateQRCodeImage(text: String, width: Int = 300, height: Int = 300): ByteArray {
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix: BitMatrix = qrCodeWriter.encode(
            text,
            BarcodeFormat.QR_CODE,
            width,
            height
        )

        ByteArrayOutputStream().use { outputStream ->
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream)
            return outputStream.toByteArray()
        }
    }

    fun generateQRCodeBase64(text: String, size: Int = 300): String {
        val imageBytes = generateQRCodeImage(text, size, size)
        return Base64.getEncoder().encodeToString(imageBytes)
    }
}