package com.elegant.software.quickpay.truelayer.outbound

import com.elegant.software.quickpay.truelayer.api.PaymentRequested
import com.elegant.software.quickpay.truelayer.api.PaymentResult
import com.elegant.software.quickpay.truelayer.support.TrueLayerProperties
import com.truelayer.java.TrueLayerClient
import com.truelayer.java.entities.CurrencyCode
import com.truelayer.java.entities.User
import com.truelayer.java.http.entities.ApiResponse
import com.truelayer.java.payments.entities.*
import com.truelayer.java.payments.entities.beneficiary.MerchantAccount
import com.truelayer.java.payments.entities.paymentmethod.PaymentMethod
import com.truelayer.java.payments.entities.providerselection.ProviderSelection
import com.truelayer.java.payments.entities.schemeselection.preselected.SchemeSelection
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.util.concurrent.CompletableFuture

@EnableConfigurationProperties(TrueLayerProperties::class)
@Component
class PaymentService(
    private val trueLayerProperties: TrueLayerProperties,
    private val trueLayerClient: TrueLayerClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun startPayment(paymentRequest: PaymentRequested): PaymentResult {
        // Convert amount to minor units (pennies/cents) - TrueLayer expects amount in minor units
        val amountInMinor: Int = (paymentRequest.amount * BigDecimal(100)).toInt()
        val currency = CurrencyCode.valueOf(paymentRequest.currency.uppercase())

        val createTrueLayerPaymentRequest = CreatePaymentRequest.builder()
            .amountInMinor(amountInMinor)
            .currency(currency)
            .paymentMethod(
                PaymentMethod.bankTransfer()
                    .providerSelection(
                        ProviderSelection.preselected()
                            .providerId("mock-payments-gb-redirect")
                            .schemeSelection(
                                SchemeSelection.preselected()
                                    .schemeId("faster_payments_service")
                                    .build()
                            )
                            .build()
                    )
                    .beneficiary(
                        MerchantAccount.builder()
                            .merchantAccountId(trueLayerProperties.merchantAccountId)
                            .build()
                    )
                    .build()
            )
            .user(
                User.builder()
                    .name("Andrea")  // TODO: Get from paymentRequest
                    .email("andrea@truelayer.com")  // TODO: Get from paymentRequest
                    .build()
            )
            .metadata(
                mapOf(
                    "paymentRequestId" to paymentRequest.paymentRequestId.toString(),
                    "orderId" to paymentRequest.orderId
                )
            )
            .build()

        log.info("[DEBUG_LOG] Prepared TrueLayer CreatePaymentRequest: {}", paymentRequest)

        // Fire the request
        val paymentResponse: CompletableFuture<ApiResponse<CreatePaymentResponse?>?> = trueLayerClient
            .payments()
            .createPayment(createTrueLayerPaymentRequest)

        // Wait for the response
        val apiResponse = paymentResponse.get()

        return verifyResult(apiResponse, paymentRequest)
    }

    private fun verifyResult(
        apiResponse: ApiResponse<CreatePaymentResponse?>?,
        paymentRequest: PaymentRequested
    ): PaymentResult {
        return when {
            apiResponse == null -> {
                log.error("TrueLayer payment API response was null for order {}", paymentRequest.orderId)
                PaymentResult(
                    paymentRequestId = paymentRequest.paymentRequestId!!,
                    status = "failed",
                    errorMessage = "TrueLayer payment API response was null",
                    amount = paymentRequest.amount.toDouble(),
                    currency = paymentRequest.currency,
                    timestamp = Instant.now()
                )
            }

            apiResponse.error != null -> {
                log.error(
                    "TrueLayer payment API call failed for order {} with error: {}",
                    paymentRequest.orderId,
                    apiResponse.error
                )
                PaymentResult(
                    paymentRequestId = paymentRequest.paymentRequestId!!,
                    status = "failed",
                    errorMessage = apiResponse.error.toString(),
                    amount = paymentRequest.amount.toDouble(),
                    currency = paymentRequest.currency,
                    timestamp = Instant.now()
                )
            }

            apiResponse.data == null -> {
                log.error("TrueLayer payment API response data was null for order {}", paymentRequest.orderId)
                PaymentResult(
                    paymentRequestId = paymentRequest.paymentRequestId!!,
                    status = "failed",
                    errorMessage = "Payment API response data was null",
                    amount = paymentRequest.amount.toDouble(),
                    currency = paymentRequest.currency,
                    timestamp = Instant.now()
                )
            }

            apiResponse.data?.id == null -> {
                log.error("TrueLayer payment ID was null in response for order {}", paymentRequest.orderId)
                PaymentResult(
                    paymentRequestId = paymentRequest.paymentRequestId!!,
                    status = "failed",
                    errorMessage = "Payment ID was null in response",
                    amount = paymentRequest.amount.toDouble(),
                    currency = paymentRequest.currency,
                    timestamp = Instant.now()
                )
            }

            else -> {
                val paymentData = apiResponse.data
                val redirectUri = trueLayerClient.hppLinkBuilder()
                    .returnUri(
                        URI.create(
                            trueLayerProperties.redirectUri ?: "https://console.truelayer.com/redirect-page"
                        )
                    )
                    .resourceToken(paymentData?.resourceToken)
                    .resourceId(paymentData?.id)
                    .build()

                log.info("TrueLayer payment created successfully: {}", paymentData?.id)

                PaymentResult(
                    paymentRequestId = paymentRequest.paymentRequestId!!,
                    status = "created",  // Initial status for TrueLayer payment
                    transactionId = paymentData?.id,
                    amount = paymentRequest.amount.toDouble(),
                    currency = paymentRequest.currency,
                    deepLink = redirectUri.toString(),  // Use deepLink field for redirect URI
                    timestamp = Instant.now()
                )
            }
        }
    }
}