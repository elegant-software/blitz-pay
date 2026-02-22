package com.elegant.software.quickpay.truelayer.inbound

import com.elegant.software.quickpay.truelayer.api.QrPaymentResponse
import com.elegant.software.quickpay.truelayer.api.QrPaymentStatus
import com.elegant.software.quickpay.truelayer.outbound.QrPaymentService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant
import java.util.UUID

@WebMvcTest(QrCodeController::class)
class QrCodeControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var qrPaymentService: QrPaymentService

    private val testPaymentId = UUID.randomUUID()

    private fun buildPaymentResponse(paymentId: UUID = testPaymentId) = QrPaymentResponse(
        success = true,
        paymentRequestId = paymentId,
        transactionId = "tx-123",
        status = QrPaymentStatus.INITIATED,
        qrCodeData = "https://payment.truelayer.com/checkout/abc",
        qrCodeImage = "base64image==",
        qrCodeUrl = "http://localhost:8080/api/qr-payments/$paymentId/image",
        deepLink = "truelayer://payment-link/abc",
        paymentUrl = "https://payment.truelayer.com/checkout/abc",
        merchant = "TestMerchant",
        amount = 5.00,
        currency = "EUR",
        expiresAt = Instant.now().plusSeconds(86400),
        message = "Scan QR code to pay 5.0 EUR at TestMerchant"
    )

    @Test
    fun `POST createQrPayment returns 201 with response body`() {
        whenever(qrPaymentService.initiateQrPayment(any())).thenReturn(buildPaymentResponse())

        mockMvc.perform(
            post("/api/qr-payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"merchant":"TestMerchant","amount":5.00,"currency":"EUR","orderDetails":"Latte"}"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("INITIATED"))
            .andExpect(jsonPath("$.merchant").value("TestMerchant"))
    }

    @Test
    fun `GET quick returns 200 with response body`() {
        whenever(qrPaymentService.initiateQrPayment(any())).thenReturn(buildPaymentResponse())

        mockMvc.perform(
            get("/api/qr-payments/quick")
                .param("merchant", "TestMerchant")
                .param("amount", "5.00")
                .param("order", "Latte")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.merchant").value("TestMerchant"))
    }

    @Test
    fun `GET health returns 200 UP`() {
        mockMvc.perform(get("/api/qr-payments/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("qr-payments"))
    }

    @Test
    fun `GET payment by ID returns 200 when found`() {
        whenever(qrPaymentService.getQrPayment(testPaymentId)).thenReturn(buildPaymentResponse())

        mockMvc.perform(get("/api/qr-payments/$testPaymentId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paymentRequestId").value(testPaymentId.toString()))
    }

    @Test
    fun `GET payment by ID returns 404 when not found`() {
        whenever(qrPaymentService.getQrPayment(any())).thenReturn(null)

        mockMvc.perform(get("/api/qr-payments/$testPaymentId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("Payment not found"))
    }

    @Test
    fun `GET payment by invalid ID returns 400`() {
        mockMvc.perform(get("/api/qr-payments/not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Invalid payment request ID"))
    }

    @Test
    fun `GET callback with success returns 200`() {
        mockMvc.perform(
            get("/api/qr-payments/callback")
                .param("payment_id", "pay-123")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.payment_id").value("pay-123"))
    }

    @Test
    fun `GET callback with error returns 200 with error status`() {
        mockMvc.perform(
            get("/api/qr-payments/callback")
                .param("error", "payment_canceled")
                .param("error_description", "User canceled the payment")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.error").value("payment_canceled"))
            .andExpect(jsonPath("$.message").value("User canceled the payment"))
    }

    @Test
    fun `GET qr image returns 200 with image bytes when found`() {
        val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG magic bytes
        whenever(qrPaymentService.getQrImage(testPaymentId)).thenReturn(imageBytes)

        mockMvc.perform(get("/api/qr-payments/$testPaymentId/image"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
    }

    @Test
    fun `GET qr image returns 404 when not found`() {
        whenever(qrPaymentService.getQrImage(any())).thenReturn(null)

        mockMvc.perform(get("/api/qr-payments/$testPaymentId/image"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("QR image not found"))
    }
}
