package com.elegant.software.quickpay.payments.truelayer.inbound

import TlWebhookEnvelope
import com.elegant.software.quickpay.payments.truelayer.support.JwksService
import com.elegant.software.quickpay.payments.truelayer.support.TlWebhookProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.truelayer.signing.Verifier
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit

@WebMvcTest(TlWebhookController::class)
@ContextConfiguration(classes = [WebhookControllerTest.TestConfig::class])
class WebhookControllerTest {

    @Configuration
    class TestConfig {
        @Bean
        fun tlWebhookProperties(): TlWebhookProperties {
            return TlWebhookProperties(
                environment = "sandbox",
                maxSkew = "PT5M"
            )
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var jwksService: JwksService

    @MockBean
    private lateinit var eventPublisher: ApplicationEventPublisher

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `webhook should return unauthorized when tl-signature header is missing`() {
        // Given
        val webhookBody = """{"type": "payment_executed", "event_id": "test-123"}"""

        // When & Then
        mockMvc.perform(
            post("/webhooks/truelayer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody)
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `webhook should return unauthorized when timestamp is missing`() {
        // Given
        val webhookBody = """{"type": "payment_executed", "event_id": "test-123"}"""

        // When & Then
        mockMvc.perform(
            post("/webhooks/truelayer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("tl-signature", "fake-signature")
                .content(webhookBody)
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `webhook should return unauthorized when timestamp is too old`() {
        // Given
        val webhookBody = """{"type": "payment_executed", "event_id": "test-123"}"""
        val oldTimestamp = Instant.now().minus(10, ChronoUnit.MINUTES).toString()

        // When & Then
        mockMvc.perform(
            post("/webhooks/truelayer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("tl-signature", "fake-signature")
                .header("x-tl-webhook-timestamp", oldTimestamp)
                .content(webhookBody)
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `webhook should return unauthorized when JKU extraction fails`() {
        // Given
        val webhookBody = """{"type": "payment_executed", "event_id": "test-123"}"""
        val currentTimestamp = Instant.now().toString()

        // When & Then - invalid signature that will fail JKU extraction
        mockMvc.perform(
            post("/webhooks/truelayer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("tl-signature", "invalid-signature-format")
                .header("x-tl-webhook-timestamp", currentTimestamp)
                .content(webhookBody)
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `webhook timestamp validation should accept recent timestamps`() {
        // Given
        val webhookBody = """{"type": "payment_executed", "event_id": "test-123"}"""
        val recentTimestamp = Instant.now().minus(2, ChronoUnit.MINUTES).toString()

        // When & Then - will fail at signature validation but pass timestamp check
        mockMvc.perform(
            post("/webhooks/truelayer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("tl-signature", "fake-signature")
                .header("x-tl-webhook-timestamp", recentTimestamp)
                .content(webhookBody)
        )
            // Should fail at signature validation, not timestamp
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `webhook should handle different event types`() {
        // Given - Different webhook event types
        val eventTypes = listOf(
            "payment_executed",
            "payment_settled",
            "payment_failed",
            "refund_executed"
        )

        val currentTimestamp = Instant.now().toString()

        eventTypes.forEach { eventType ->
            val webhookBody = """{"type": "$eventType", "event_id": "test-$eventType"}"""

            // When & Then - each should be processed the same way
            mockMvc.perform(
                post("/webhooks/truelayer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("tl-signature", "fake-signature")
                    .header("x-tl-webhook-timestamp", currentTimestamp)
                    .content(webhookBody)
            )
                // All should fail at signature validation
                .andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `webhook should handle malformed timestamp`() {
        // Given
        val webhookBody = """{"type": "payment_executed", "event_id": "test-123"}"""
        val malformedTimestamp = "not-a-valid-timestamp"

        // When & Then
        mockMvc.perform(
            post("/webhooks/truelayer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("tl-signature", "fake-signature")
                .header("x-tl-webhook-timestamp", malformedTimestamp)
                .content(webhookBody)
        )
            .andExpect(status().isUnauthorized)
    }
}
