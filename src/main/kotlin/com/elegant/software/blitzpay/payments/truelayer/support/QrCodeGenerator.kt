package com.elegant.software.blitzpay.payments.truelayer.support

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
        val paymentUrl: String,
        val deepLink: String,
        val qrCodeImage: String
    )

    fun generatePaymentQRCode(
        paymentId: String,
        amount: Long,
        currency: String = "GBP",
        paymentUrl: String
    ): QrCodeResult {
        logger.info { "Generating QR code for payment: $paymentId" }

        val payloadJson = """{"id":"$paymentId","a":$amount,"c":"$currency","u":"$paymentUrl"}"""
        val deepLink = "truelayer://payment/$paymentId"
        val qrCodeImage = generateQRCodeBase64(payloadJson)

        return QrCodeResult(
            paymentUrl = paymentUrl,
            deepLink = deepLink,
            qrCodeImage = qrCodeImage
        )
    }

    fun generateQRCodeImage(text: String, width: Int = 300, height: Int = 300): ByteArray {
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix: BitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height)
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
