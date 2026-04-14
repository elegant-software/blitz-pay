package com.elegant.software.blitzpay.payments.truelayer.outbound

import com.elegant.software.blitzpay.payments.truelayer.api.PaymentGateway
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentRequested
import com.elegant.software.blitzpay.payments.truelayer.api.PaymentResult
import com.elegant.software.blitzpay.payments.truelayer.support.TrueLayerProperties
import com.truelayer.java.TrueLayerClient
import com.truelayer.java.entities.CurrencyCode
import com.truelayer.java.entities.ProviderFilter
import com.truelayer.java.entities.User
import com.truelayer.java.http.entities.ApiResponse
import com.truelayer.java.payments.entities.*
import com.truelayer.java.payments.entities.beneficiary.MerchantAccount
import com.truelayer.java.payments.entities.paymentmethod.PaymentMethod
import com.truelayer.java.payments.entities.providerselection.ProviderSelection
import com.truelayer.java.payments.entities.schemeselection.preselected.SchemeSelection
import org.apache.commons.lang3.concurrent.UncheckedFuture.map
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.net.URI
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * TrueLayer payment gateway adapter.
 *
 * For now, we DO NOT perform any outbound API call. Instead, we:
 *  - Build a request-shaped object equivalent to TrueLayer's CreatePaymentRequest
 *  - Log it for traceability
 *  - Return a deterministic placeholder payment id
 *
 * This can be swapped later with the official TrueLayer Java SDK call, using the
 * same fields assembled below.
 */
@EnableConfigurationProperties(TrueLayerProperties::class)
@Component
class PaymentService(
    private val trueLayerProperties: TrueLayerProperties,
    private val trueLayerClient: TrueLayerClient
) : PaymentGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun startPayment(paymentRequest: PaymentRequested): PaymentResult {
        // Adapt types for SDK requirements
        val amountInMinor = paymentRequest.amountMinorUnits.toInt() // SDK likely expects Int
        val currency = CurrencyCode.valueOf(paymentRequest.currency.uppercase()) // Use SDK's CurrencyCode enum
        val createTrueLayerPaymentRequest = CreatePaymentRequest.builder()
            .amountInMinor(amountInMinor)
            .currency(currency)
            .paymentMethod(
                PaymentMethod.bankTransfer()
                    .providerSelection(
                        ProviderSelection.preselected().providerId("mock-payments-gb-redirect").schemeSelection( SchemeSelection.preselected().schemeId("faster_payments_service").build())
                            /*.filter(
                                ProviderFilter.builder()
                                    .countries(Collections.singletonList(CountryCode.GB))
                                    .releaseChannel(ReleaseChannel.GENERAL_AVAILABILITY)
                                    .customerSegments(Collections.singletonList(CustomerSegment.RETAIL))
                                    .providerIds(Collections.singletonList("mock-payments-gb-redirect"))
                                    .build()
                            )*/
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
                    .name("Andrea")
                    .email("andrea@truelayer.com")
                    .build()
            ).metadata(mapOf("paymentRequestId" to paymentRequest.paymentRequestId.toString(),
                "orderId" to paymentRequest.orderId))
            .build()

        log.info("[DEBUG_LOG] Prepared TrueLayer CreatePaymentRequest: {}", paymentRequest)

        // fire the request
        val paymentResponse: CompletableFuture<ApiResponse<CreatePaymentResponse?>?> = trueLayerClient
            .payments()
            .createPayment(createTrueLayerPaymentRequest)

        // wait for the response
        val apiResponse = paymentResponse.get()
        fun verifyResult(): PaymentResult {
            if (apiResponse == null) {
                log.error("TrueLayer payment API response was null for order {}", paymentRequest.orderId)
                return PaymentResult(
                    paymentRequestId = paymentRequest.paymentRequestId!!,
                    orderId = paymentRequest.orderId,
                    error = "TrueLayer payment API response was null"
                )
            }
            if (apiResponse.error != null) {
                log.error(
                    "TrueLayer payment API call failed for order {} with response {}",
                    paymentRequest.orderId,
                    apiResponse.error
                )
                return PaymentResult(paymentRequestId = paymentRequest.paymentRequestId!!,orderId = paymentRequest.orderId, error = apiResponse.error.toString())
            }
            val paymentData = apiResponse.data
            if (paymentData == null) {
                log.error("TrueLayer payment API response data was null for order {}", paymentRequest.orderId)
                return PaymentResult(paymentRequestId = paymentRequest.paymentRequestId!!,orderId = paymentRequest.orderId, error = "Payment API response data was null")
            }
            val paymentId = paymentData.id
            if (paymentId == null) {
                log.error("TrueLayer payment ID was null in response for the order {}", paymentRequest.orderId)
                return PaymentResult(paymentRequestId = paymentRequest.paymentRequestId!!,orderId = paymentRequest.orderId, error = "Payment ID was null in response")
            }
            val redirectURI =
                trueLayerClient.hppLinkBuilder().returnUri(URI.create("https://console.truelayer.com/redirect-page"))
                    .resourceToken(paymentData.resourceToken)
                    .resourceId(paymentId)
                    .build()
            return PaymentResult(
                paymentRequestId = paymentRequest.paymentRequestId!!,
                orderId = paymentRequest.orderId,
                paymentId = paymentId,
                resourceToken = paymentData.resourceToken,
                redirectReturnUri = paymentRequest.redirectReturnUri,
                redirectURI = redirectURI
            )
        }

        return verifyResult()
    }
}