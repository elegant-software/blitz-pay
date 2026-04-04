package com.elegant.software.blitzpay.mobileobservability

import com.elegant.software.blitzpay.mobileobservability.api.MobileLogsForwarder
import com.elegant.software.blitzpay.payments.QuickpayApplication
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(MobileLogsController::class)
@ContextConfiguration(classes = [QuickpayApplication::class])
class MobileLogsControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var forwarder: MobileLogsForwarder

    @Test
    fun `POST mobile-logs returns 202 with accepted count`() {
        whenever(forwarder.forward(any())).thenReturn(2)

        webTestClient.post()
            .uri("/v1/observability/mobile-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "context": {
                    "serviceName": "blitzpay-mobile",
                    "sessionId": "sess-123",
                    "osName": "Android",
                    "osVersion": "14"
                  },
                  "events": [
                    { "message": "App launched", "severityText": "INFO" },
                    { "message": "Screen loaded", "severityText": "DEBUG" }
                  ]
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("$.accepted").isEqualTo(2)

        verify(forwarder).forward(any())
    }

    @Test
    fun `POST mobile-logs with empty events returns 400`() {
        webTestClient.post()
            .uri("/v1/observability/mobile-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{ "events": [] }""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `POST mobile-logs with blank message returns 400`() {
        webTestClient.post()
            .uri("/v1/observability/mobile-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "events": [{ "message": "   " }]
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isBadRequest
    }
}
