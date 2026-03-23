package com.elegant.software.blitzpay.contract

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class PaymentsContractTest : ContractVerifierBase() {

    @Test
    fun `should create payment request`() {
        webTestClient.post()
            .uri("/v1/payments/request")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "paymentRequestId": null,
                  "orderId": "ORDER-123",
                  "amountMinorUnits": 1099,
                  "currency": "EUR",
                  "userDisplayName": "Jane Doe",
                  "redirectReturnUri": "https://merchant.example.com/return"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isAccepted
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.paymentRequestId")
            .value<String> { paymentRequestId ->
                require(Regex("[0-9a-fA-F\\-]{36}").matches(paymentRequestId)) {
                    "Expected UUID-like paymentRequestId but got '$paymentRequestId'"
                }
            }
    }
}
