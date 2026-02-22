package com.elegant.software.quickpay.truelayer.outbound

import com.elegant.software.quickpay.support.PaymentUpdateBus
import com.elegant.software.quickpay.truelayer.api.*
import com.elegant.software.quickpay.truelayer.support.QrCodeGenerator
import com.elegant.software.quickpay.truelayer.support.QrCodeProperties
import com.elegant.software.quickpay.truelayer.support.TrueLayerProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.truelayer.signing.Signer
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class QrPaymentService(
    private val qrCodeGenerator: QrCodeGenerator,
    private val paymentUpdateBus: PaymentUpdateBus,
    private val trueLayerTokenService: TrueLayerTokenService,
    private val webClient: WebClient,
    private val trueLayerProperties: TrueLayerProperties,
    private val qrCodeProperties: QrCodeProperties
) {
    private val logger = KotlinLogging.logger {}
    private val qrPayments = ConcurrentHashMap<UUID, QrPaymentResponse>()

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private val PAYMENT_LINKS_URL = "https://api.truelayer-sandbox.com/v3/payment-links"

    fun initiateQrPayment(request: QrPaymentRequest): QrPaymentResponse {
        logger.info { "=== INITIATING QR PAYMENT ===" }
        logger.info { "Request: $request" }

        return try {
            logger.info { "Step 1: Creating TrueLayer payment link..." }

            // 1. Create TrueLayer payment link
            val paymentLink = try {
                createTrueLayerPaymentLink(request)
            } catch (e: Exception) {
                logger.error(e) { "FAILED in createTrueLayerPaymentLink: ${e.javaClass.simpleName} - ${e.message}" }
                throw e
            }

            logger.info { "✅ TrueLayer Payment Link Created: ID=${paymentLink.id}, URI=${paymentLink.uri}" }

            // 2. Generate QR code from payment link URI
            logger.info { "Step 2: Generating QR code..." }
            val qrResult = qrCodeGenerator.generatePaymentQRCode(
                paymentRequestId = request.paymentRequestId,
                amount = request.amount.toString(),
                merchant = request.merchant,
                currency = request.currency,
                paymentUrl = paymentLink.uri
            )

            logger.info { "✅ QR Code Generated" }

            // 3. Create QR image URL endpoint
            val qrCodeUrl = "${getBaseUrl()}/api/qr-payments/${request.paymentRequestId}/image"

            // 4. Create response
            logger.info { "Step 3: Building response..." }
            val response = buildQrPaymentResponse(request, paymentLink, qrResult, qrCodeUrl)

            // 5. Store in memory cache
            qrPayments[request.paymentRequestId] = response

            // 6. Publish to event bus
            publishToBus(
                request.paymentRequestId, QrPaymentStatus.INITIATED, paymentLink.uri, createDeepLink(paymentLink.id)
            )

            logger.info { "✅ QR payment initiated: ${request.paymentRequestId}, TrueLayer ID: ${paymentLink.id}" }
            response

        } catch (e: Exception) {
            logger.error(e) { "❌ CRITICAL ERROR in initiateQrPayment" }
            logger.error { "Error type: ${e.javaClass.name}" }
            logger.error { "Error message: ${e.message}" }
            logger.error { "Stack trace:" }
            e.printStackTrace()

            createErrorResponse(
                paymentRequestId = request.paymentRequestId,
                error = "Failed to create payment link: ${e.message ?: "Unknown error"}"
            )
        }
    }

    private fun createTrueLayerPaymentLink(request: QrPaymentRequest): TrueLayerPaymentResponse {
        // 1. Get access token
        val accessToken = trueLayerTokenService.fetchToken()
        logger.debug { "TrueLayer access token -> $accessToken" }

        // 2. Build TrueLayer request object
        val trueLayerRequest = buildTrueLayerRequest(request)

        // 3. Serialize to JSON deterministically
        val mapper = jacksonObjectMapper()
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        val requestJson = mapper.writeValueAsString(trueLayerRequest)

        // 4. Generate idempotency key
        val idempotencyKey = UUID.randomUUID().toString()

        // 5. Generate TL-Signature using the exact JSON string
        logger.info { "Generating Tl-Signature for request..." }
//        val tlSignature =
//            signatureGenerator.generateTlSignature("POST", "/v3/payment-links", idempotencyKey, requestJson)
        // assume props.privateKeyPath is path to your PEM file
        val pem = Files.readString(Path.of(trueLayerProperties.privateKeyPath))
        val privateKeyPem = pem  // include the header/footer as-is

// build the signature
        val tlSignature = Signer.from(trueLayerProperties.keyId, privateKeyPem)
            .header("Idempotency-Key", idempotencyKey)
            .method("post")
            .path("/v3/payment-links")
            .body(requestJson)     // requestJson: exactly the JSON you will send
            .sign()
        logger.info { "Request JSON (first 500 chars): ${requestJson.take(500)}" }
        logger.info { "Generated Tl-Signature (first 100 chars): ${tlSignature}" }

        // 6. Send request with exact JSON string
        val response = webClient.post()
            .uri(PAYMENT_LINKS_URL)
            .header("Authorization", "Bearer $accessToken")
            .header("Idempotency-Key", idempotencyKey)
            .header("Tl-Signature", tlSignature)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestJson)
            .retrieve()
            .onStatus({ it.is4xxClientError }) { clientResponse ->
                clientResponse.bodyToMono(String::class.java)
                    .doOnNext { body ->
                        logger.error {
                            """
                        🔴 TrueLayer API Client Error (${clientResponse.statusCode()}):
                        Headers: ${clientResponse.headers()}
                        Body: $body
                        
                        Request details:
                        - Token (first 20): ${accessToken.take(20)}...
                        - Signature (first 50): ${tlSignature.take(50)}...
                        - Merchant Account: ${trueLayerProperties.merchantAccountId}
                    """.trimIndent()
                        }
                    }
                    .flatMap { body ->
                        Mono.error(RuntimeException("TrueLayer API error ${clientResponse.statusCode()}: $body"))
                    }
            }
            .bodyToMono(TrueLayerPaymentResponse::class.java)
            .block()
            ?: throw RuntimeException("Null response from TrueLayer")

        logger.info { "Payment link created successfully: ${response.id}" }
        return response
    }


    private fun buildTrueLayerRequest(request: QrPaymentRequest): TrueLayerPaymentRequest {
        // Convert amount to minor units (pennies/cents)
        val amountInMinor = (request.amount * 100).toLong()

        // Generate reference
        val reference = generateReference(request)

        // Set expiration (24 hours from now)

        val expiresAt = Instant.now()
            .plusSeconds(24 * 60 * 60)
            .atZone(ZoneId.of("UTC"))
            .format(DATE_FORMATTER)

        // Parse order details into product items
        val productItems = parseOrderItems(request.orderDetails, amountInMinor)

        return TrueLayerPaymentRequest(
            expiresAt = expiresAt,
            reference = reference,
            returnUri = trueLayerProperties.redirectUri,
            paymentConfiguration = TrueLayerPaymentRequest.PaymentConfiguration(
                amountInMinor = amountInMinor,
                currency = request.currency.uppercase(),
                paymentMethod = TrueLayerPaymentRequest.PaymentMethod(
                    beneficiary = TrueLayerPaymentRequest.Beneficiary(
                        merchantAccountId = trueLayerProperties.merchantAccountId
                    )
                ),
                user = TrueLayerPaymentRequest.PaymentUser(
                    id = UUID.randomUUID().toString(),
                    name = "Coffee Shop Customer",
                    email = "customer@example.com" // placeholder for now
                )
            ),
            productItems = productItems,
            type = "single_payment"
        )
    }

    private fun generateReference(request: QrPaymentRequest): String {
        return "COFFEE_${request.paymentRequestId.toString().substring(0, 8).uppercase()}"
    }

    private fun generateCustomerId(): String {
        return "cust_${UUID.randomUUID().toString().substring(0, 8)}"
    }

    private fun parseOrderItems(
        orderDetails: String, totalAmountInMinor: Long
    ): List<TrueLayerPaymentRequest.ProductItem> {
        // Simple parsing for coffee orders
        val items = mutableListOf<TrueLayerPaymentRequest.ProductItem>()
        val parts = orderDetails.split(",").map { it.trim() }

        if (parts.isEmpty()) {
            // Fallback: single item
            items.add(
                TrueLayerPaymentRequest.ProductItem(
                    name = "Coffee Order",
                    priceInMinor = totalAmountInMinor,
                    quantity = 1,
                    url = "https://your-coffee-shop.com"
                )
            )
        } else if (parts.size == 1 && !parts[0].contains(Regex("\\d"))) {
            // Single item without quantity
            val item = parts[0]
            items.add(
                TrueLayerPaymentRequest.ProductItem(
                    name = item,
                    priceInMinor = totalAmountInMinor,
                    quantity = 1,
                    url = "https://your-coffee-shop.com/menu/${item.lowercase().replace(" ", "-")}"
                )
            )
        } else {
            // Multiple items with quantities
            var remainingAmount = totalAmountInMinor

            parts.forEachIndexed { index, part ->
                val quantity = extractQuantity(part)
                val itemName = extractItemName(part)
                val pricePerItem = calculateItemPrice(itemName, quantity, index, parts.size, remainingAmount)

                items.add(
                    TrueLayerPaymentRequest.ProductItem(
                        name = itemName,
                        priceInMinor = pricePerItem,
                        quantity = quantity,
                        url = "https://your-coffee-shop.com/menu/${itemName.lowercase().replace(" ", "-")}"
                    )
                )

                remainingAmount -= pricePerItem * quantity
            }
        }

        return items
    }

    private fun extractQuantity(item: String): Int {
        val match = Regex("(\\d+)\\s+.+").find(item)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    private fun extractItemName(item: String): String {
        return item.replace(Regex("^\\d+\\s*"), "").trim()
    }

    private fun calculateItemPrice(
        itemName: String, quantity: Int, index: Int, totalItems: Int, remainingAmount: Long
    ): Long {
        // If last item, use remaining amount
        if (index == totalItems - 1) {
            return remainingAmount / quantity
        }

        // Otherwise use predefined prices
        val unitPrice = when (itemName.lowercase()) {
            "latte" -> 400L // £4.00
            "cappuccino" -> 350L // £3.50
            "espresso" -> 250L // £2.50
            "flat white" -> 380L // £3.80
            "mocha" -> 420L // £4.20
            "tea" -> 200L // £2.00
            else -> 300L // Default
        }
        return unitPrice
    }

    private fun buildQrPaymentResponse(
        request: QrPaymentRequest,
        paymentLink: TrueLayerPaymentResponse,
        qrResult: QrCodeGenerator.QrCodeResult,
        qrCodeUrl: String
    ): QrPaymentResponse {
        return QrPaymentResponse(
            success = true,
            paymentRequestId = request.paymentRequestId,
            transactionId = paymentLink.id,
            status = QrPaymentStatus.INITIATED,
            qrCodeData = paymentLink.uri,
            qrCodeImage = qrResult.qrCodeImage,
            qrCodeUrl = qrCodeUrl,
            deepLink = createDeepLink(paymentLink.id),
            paymentUrl = paymentLink.uri,
            merchant = request.merchant,
            amount = request.amount,
            currency = request.currency,
            expiresAt = Instant.now().plusSeconds(24 * 60 * 60), // 24 hours
            message = "Scan QR code to pay ${request.amount} ${request.currency} at ${request.merchant}"
        )
    }

    private fun createDeepLink(paymentLinkId: String): String {
        return "truelayer://payment-link/$paymentLinkId"
    }

    private fun getBaseUrl(): String {
        return qrCodeProperties.server.baseUrl
    }

    private fun createErrorResponse(
        paymentRequestId: UUID, error: String
    ): QrPaymentResponse {
        return QrPaymentResponse(
            success = false,
            paymentRequestId = paymentRequestId,
            transactionId = "ERROR-${paymentRequestId.toString().take(8)}",
            status = QrPaymentStatus.FAILED,
            error = error,
            expiresAt = Instant.now(),
            message = "Failed to create payment"
        )
    }

    private fun publishToBus(
        paymentRequestId: UUID, status: QrPaymentStatus, qrCodeData: String? = null, deepLink: String? = null
    ) {
        val paymentResult = PaymentResult(
            paymentRequestId = paymentRequestId,
            status = status.name,
            qrCodeData = qrCodeData,
            qrStatus = status.name,
            deepLink = deepLink,
            timestamp = Instant.now()
        )

        paymentUpdateBus.emit(paymentRequestId, paymentResult)
    }

    // Get QR image as bytes (for the image endpoint)
    fun getQrImage(paymentRequestId: UUID): ByteArray? {
        val payment = qrPayments[paymentRequestId]
        return payment?.qrCodeImage?.let {
            Base64.getDecoder().decode(it)
        }
    }

    // Get QR payment by ID
    fun getQrPayment(paymentRequestId: UUID): QrPaymentResponse? {
        return qrPayments[paymentRequestId]
    }

    fun updatePaymentStatus(
        paymentRequestId: UUID, status: QrPaymentStatus, paymentResult: PaymentResult? = null
    ) {
        val currentPayment = qrPayments[paymentRequestId] ?: return

        val updatedPayment = currentPayment.copy(
            status = status, message = when (status) {
                QrPaymentStatus.SCANNED -> "QR code scanned, processing payment..."
                QrPaymentStatus.PROCESSING -> "Payment being processed"
                QrPaymentStatus.SUCCESS -> "Payment successful"
                QrPaymentStatus.FAILED -> "Payment failed"
                QrPaymentStatus.EXPIRED -> "QR code expired"
                QrPaymentStatus.CANCELLED -> "Payment cancelled"
                else -> currentPayment.message
            }
        )

        qrPayments[paymentRequestId] = updatedPayment

        val busUpdate = paymentResult ?: PaymentResult(
            paymentRequestId = paymentRequestId,
            status = status.name,
            qrCodeData = updatedPayment.qrCodeData,
            qrStatus = status.name,
            deepLink = updatedPayment.deepLink,
            timestamp = Instant.now()
        )

        paymentUpdateBus.emit(paymentRequestId, busUpdate)
        logger.info { "Payment status updated: $paymentRequestId -> $status" }
    }

    fun handlePaymentResult(paymentRequestId: UUID, paymentResult: PaymentResult) {
        val status = when (paymentResult.status.lowercase()) {
            "executed", "settled", "completed", "success" -> QrPaymentStatus.SUCCESS
            "failed", "declined", "rejected", "cancelled" -> QrPaymentStatus.FAILED
            "pending", "processing" -> QrPaymentStatus.PROCESSING
            else -> QrPaymentStatus.PROCESSING
        }

        updatePaymentStatus(paymentRequestId, status, paymentResult)
    }

    fun markQrScanned(paymentRequestId: UUID) {
        updatePaymentStatus(paymentRequestId, QrPaymentStatus.SCANNED)
    }

    fun markQrProcessing(paymentRequestId: UUID) {
        updatePaymentStatus(paymentRequestId, QrPaymentStatus.PROCESSING)
    }

    fun cleanupExpiredPayments() {
        val now = Instant.now()
        qrPayments.entries.removeAll { (paymentRequestId, payment) ->
            if (payment.expiresAt.isBefore(now)) {
                updatePaymentStatus(paymentRequestId, QrPaymentStatus.EXPIRED)
                true
            } else {
                false
            }
        }

        if (qrPayments.isEmpty()) {
            logger.debug { "No expired payments to clean up" }
        } else {
            logger.debug { "Cleaned up expired payments. Remaining: ${qrPayments.size}" }
        }
    }

    companion object {
        fun createQuickPayment(
            merchant: String, amount: Double, orderDetails: String, currency: String = "GBP"
        ): QrPaymentRequest {
            return QrPaymentRequest(
                merchant = merchant, amount = amount, currency = currency, orderDetails = orderDetails
            )
        }
    }
}