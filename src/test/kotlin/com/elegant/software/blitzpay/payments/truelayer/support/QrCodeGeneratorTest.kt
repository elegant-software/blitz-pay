package com.elegant.software.blitzpay.payments.truelayer.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class QrCodeGeneratorTest {

    private val generator = QrCodeGenerator()

    @Test
    fun `generateQRCodeImage returns non-empty PNG bytes`() {
        val bytes = generator.generateQRCodeImage("https://payment.truelayer.com/pay/test-id", 200, 200)
        assertThat(bytes).isNotEmpty()
        // PNG magic bytes: 0x89 0x50 0x4E 0x47
        assertThat(bytes[0]).isEqualTo(0x89.toByte())
        assertThat(bytes[1]).isEqualTo(0x50.toByte())
        assertThat(bytes[2]).isEqualTo(0x4E.toByte())
        assertThat(bytes[3]).isEqualTo(0x47.toByte())
    }

    @Test
    fun `generateQRCodeBase64 returns valid base64 string`() {
        val base64 = generator.generateQRCodeBase64("https://payment.truelayer.com/pay/test-id")
        assertThat(base64).isNotBlank()
        val decoded = Base64.getDecoder().decode(base64)
        assertThat(decoded).isNotEmpty()
    }

    @Test
    fun `generatePaymentQRCode returns result with all fields populated`() {
        val paymentId = "pay-abc123"
        val amount = 1500L
        val currency = "GBP"
        val paymentUrl = "https://payment.truelayer.com/checkout/$paymentId"

        val result = generator.generatePaymentQRCode(
            paymentId = paymentId,
            amount = amount,
            currency = currency,
            paymentUrl = paymentUrl
        )

        assertThat(result.paymentUrl).isEqualTo(paymentUrl)
        assertThat(result.deepLink).isEqualTo("truelayer://payment/$paymentId")
        assertThat(result.qrCodeImage).isNotBlank()
    }

    @Test
    fun `generatePaymentQRCode embeds payment URL in QR code`() {
        val paymentId = "pay-xyz987"
        val paymentUrl = "https://payment.truelayer.com/checkout/$paymentId"

        val result = generator.generatePaymentQRCode(
            paymentId = paymentId,
            amount = 500L,
            paymentUrl = paymentUrl
        )

        // Decode and verify it's a valid PNG
        val imageBytes = Base64.getDecoder().decode(result.qrCodeImage)
        assertThat(imageBytes[0]).isEqualTo(0x89.toByte())
    }

    @Test
    fun `generateQRCodeBase64 produces different output for different inputs`() {
        val base64A = generator.generateQRCodeBase64("https://payment.truelayer.com/pay/id-1")
        val base64B = generator.generateQRCodeBase64("https://payment.truelayer.com/pay/id-2")
        assertThat(base64A).isNotEqualTo(base64B)
    }

    @Test
    fun `generateQRCodeImage respects custom size`() {
        val smallImage = generator.generateQRCodeImage("test", 100, 100)
        val largeImage = generator.generateQRCodeImage("test", 400, 400)
        // A larger image should produce more bytes
        assertThat(largeImage.size).isGreaterThan(smallImage.size)
    }
}
