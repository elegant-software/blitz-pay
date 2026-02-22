package com.elegant.software.quickpay.truelayer.inbound

import com.elegant.software.quickpay.truelayer.api.QrPaymentRequest
import com.elegant.software.quickpay.truelayer.outbound.QrPaymentService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/qr-payments")
class QrCodeController(
    private val qrPaymentService: QrPaymentService
) {
    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "QrCodeController initialized" }
    }

    /**
     * Create a QR payment from a JSON request body.
     */
    @PostMapping
    fun createQrPayment(@RequestBody request: QrPaymentRequest): ResponseEntity<Any> {
        logger.info { "=== CREATE QR PAYMENT REQUEST RECEIVED ===" }
        logger.info { "  merchant: ${request.merchant}, amount: ${request.amount}, currency: ${request.currency}" }

        return try {
            val response = qrPaymentService.initiateQrPayment(request)
            logger.info { "✅ QR payment created: ${response.paymentRequestId}" }
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to create QR payment" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                    mapOf(
                        "error" to "Failed to create QR payment",
                        "message" to e.message
                    )
                )
        }
    }

    /**
     * Quick endpoint for mobile apps (simple parameters) - MUST BE FIRST!
     */
    @GetMapping("/quick")
    fun quickQrPayment(
        @RequestParam merchant: String,
        @RequestParam amount: Double,
        @RequestParam order: String,
        @RequestParam(required = false, defaultValue = "EUR") currency: String
    ): ResponseEntity<Any> {
        logger.info { "=== QUICK QR PAYMENT REQUEST RECEIVED ===" }
        logger.info { "  merchant: $merchant, amount: $amount, order: $order, currency: $currency" }

        return try {
            val request = QrPaymentRequest(
                merchant = merchant,
                amount = amount,
                currency = currency,
                orderDetails = order
            )

            val response = qrPaymentService.initiateQrPayment(request)
            logger.info { "✅ QR payment created: ${response.paymentRequestId}" }

            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to create QR payment" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                    mapOf(
                        "error" to "Failed to create QR payment",
                        "message" to e.message
                    )
                )
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        logger.info { "Health check called" }
        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "service" to "qr-payments",
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    /**
     * Get QR payment by ID - MUST BE AFTER /quick
     */
    @GetMapping("/{paymentRequestId}")
    fun getQrPayment(@PathVariable paymentRequestId: String): ResponseEntity<Any> {
        logger.info { "Get QR payment request for ID: $paymentRequestId" }

        return try {
            val uuid = java.util.UUID.fromString(paymentRequestId)
            qrPaymentService.getQrPayment(uuid)?.let { payment ->
                ResponseEntity.ok(payment)
            } ?: ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                    mapOf(
                        "error" to "Payment not found",
                        "paymentRequestId" to paymentRequestId
                    )
                )
        } catch (e: IllegalArgumentException) {
            logger.error { "Invalid payment request ID: $paymentRequestId" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "Invalid payment request ID"))
        }
    }

    @GetMapping("/{paymentRequestId}/image")
    fun getQrImage(@PathVariable paymentRequestId: String): ResponseEntity<Any> {
        logger.info { "Get QR image for ID: $paymentRequestId" }

        return try {
            val uuid = UUID.fromString(paymentRequestId)
            val imageBytes = qrPaymentService.getQrImage(uuid)

            if (imageBytes != null) {
                ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(imageBytes)
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                        mapOf(
                            "error" to "QR image not found",
                            "paymentRequestId" to paymentRequestId
                        )
                    )
            }
        } catch (e: IllegalArgumentException) {
            logger.error { "Invalid payment request ID: $paymentRequestId" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "Invalid payment request ID"))
        }
    }

    /**
     * Callback endpoint for TrueLayer payment redirect after user completes payment.
     */
    @GetMapping("/callback")
    fun paymentCallback(
        @RequestParam(name = "payment_id", required = false) paymentId: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(name = "error_description", required = false) errorDescription: String?
    ): ResponseEntity<Map<String, Any?>> {
        logger.info { "Payment callback received: payment_id=$paymentId, error=$error" }

        return if (error != null) {
            logger.warn { "Payment callback error: $error - $errorDescription" }
            ResponseEntity.ok(
                mapOf(
                    "status" to "error",
                    "error" to error,
                    "message" to (errorDescription ?: "Payment was not completed")
                )
            )
        } else {
            logger.info { "Payment callback success for payment_id=$paymentId" }
            ResponseEntity.ok(
                mapOf(
                    "status" to "success",
                    "payment_id" to paymentId,
                    "message" to "Payment completed successfully"
                )
            )
        }
    }
}