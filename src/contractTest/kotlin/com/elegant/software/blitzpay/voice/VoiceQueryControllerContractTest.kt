package com.elegant.software.blitzpay.voice

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.voice.api.AssistantResponse
import com.elegant.software.blitzpay.voice.api.ProductMatch
import com.elegant.software.blitzpay.voice.internal.MissingAudioException
import com.elegant.software.blitzpay.voice.internal.NoSpeechDetectedException
import com.elegant.software.blitzpay.voice.internal.UpstreamAiException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import java.math.BigDecimal
import java.util.UUID

class VoiceQueryControllerContractTest : ContractVerifierBase() {

    @Test
    fun `returns 200 TRANSCRIPT for valid authenticated request without merchant context`() {
        whenever(voiceGateway.process(any())).thenReturn(
            AssistantResponse.Transcript(
                transcript = "What is my latest payment?",
                language = "en",
            )
        )

        webTestClient.post().uri("/v1/voice/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken("user-123"))
            .bodyValue(multipart("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3)))
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("TRANSCRIPT")
            .jsonPath("$.transcript").isEqualTo("What is my latest payment?")
            .jsonPath("$.language").isEqualTo("en")
    }

    @Test
    fun `returns 200 PRODUCT_RESULT when single product matched`() {
        val productId = UUID.fromString("e3b0c442-98fc-1c14-9af7-c7e2d1f8a123")
        val branchId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

        whenever(voiceGateway.process(any())).thenReturn(
            AssistantResponse.ProductResult(
                products = listOf(
                    ProductMatch(
                        productId = productId,
                        branchId = branchId,
                        name = "Erdbeer Becher",
                        description = "Fresh strawberry cup",
                        unitPrice = BigDecimal("3.50"),
                        imageUrl = null,
                    )
                ),
                requestedQuantity = 1,
            )
        )

        webTestClient.post().uri("/v1/voice/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken("user-123"))
            .bodyValue(multipartWithMerchant("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3), branchId, branchId))
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("PRODUCT_RESULT")
            .jsonPath("$.requestedQuantity").isEqualTo(1)
            .jsonPath("$.products[0].name").isEqualTo("Erdbeer Becher")
            .jsonPath("$.products[0].unitPrice").isEqualTo(3.50)
    }

    @Test
    fun `returns 200 PRODUCT_RESULT with multiple candidates for ambiguous query`() {
        val branchId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

        whenever(voiceGateway.process(any())).thenReturn(
            AssistantResponse.ProductResult(
                products = listOf(
                    ProductMatch(UUID.randomUUID(), branchId, "Erdbeer Becher", null, BigDecimal("3.50"), null),
                    ProductMatch(UUID.randomUUID(), branchId, "Erdbeer Shake", null, BigDecimal("4.20"), null),
                    ProductMatch(UUID.randomUUID(), branchId, "Erdbeer Torte", null, BigDecimal("5.00"), null),
                ),
                requestedQuantity = null,
            )
        )

        webTestClient.post().uri("/v1/voice/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken("user-123"))
            .bodyValue(multipartWithMerchant("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3), branchId, branchId))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.type").isEqualTo("PRODUCT_RESULT")
            .jsonPath("$.products.length()").isEqualTo(3)
    }

    @Test
    fun `returns 200 NO_MATCH when no product matched`() {
        whenever(voiceGateway.process(any())).thenReturn(
            AssistantResponse.NoMatch(
                message = "I didn't understand that request — try browsing the product screen."
            )
        )

        webTestClient.post().uri("/v1/voice/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken("user-123"))
            .bodyValue(multipartWithMerchant("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3), UUID.randomUUID(), UUID.randomUUID()))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.type").isEqualTo("NO_MATCH")
            .jsonPath("$.message").isNotEmpty
    }

    @Test
    fun `returns 401 when bearer token missing`() {
        webTestClient.post().uri("/v1/voice/query")
            .bodyValue(multipart("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3)))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.reason").isEqualTo("MISSING_AUTHORIZATION")
    }

    @Test
    fun `returns 400 when audio missing`() {
        whenever(voiceGateway.process(any())).thenThrow(MissingAudioException())

        val builder = MultipartBodyBuilder()
        builder.part("unused", "value")

        webTestClient.post().uri("/v1/voice/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken("user-123"))
            .bodyValue(builder.build())
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.reason").isEqualTo("MISSING_AUDIO")
    }

    @Test
    fun `returns 400 when no speech detected`() {
        whenever(voiceGateway.process(any())).thenThrow(NoSpeechDetectedException())

        webTestClient.post().uri("/v1/voice/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken("user-123"))
            .bodyValue(multipart("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3)))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.reason").isEqualTo("NO_SPEECH_DETECTED")
    }

    @Test
    fun `returns 502 when upstream processing fails`() {
        whenever(voiceGateway.process(any())).thenThrow(UpstreamAiException("Voice processing is temporarily unavailable."))

        webTestClient.post().uri("/v1/voice/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken("user-123"))
            .bodyValue(multipart("audio.mp4", "audio/mp4", byteArrayOf(1, 2, 3)))
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.reason").isEqualTo("UPSTREAM_AI_ERROR")
    }

    private fun multipart(
        filename: String,
        contentType: String,
        bytes: ByteArray,
    ): org.springframework.util.MultiValueMap<String, HttpEntity<*>> {
        val builder = MultipartBodyBuilder()
        builder.part(
            "audio",
            object : ByteArrayResource(bytes) {
                override fun getFilename(): String = filename
            }
        ).contentType(MediaType.parseMediaType(contentType))
        return builder.build()
    }

    private fun multipartWithMerchant(
        filename: String,
        contentType: String,
        bytes: ByteArray,
        merchantId: UUID,
        branchId: UUID,
    ): org.springframework.util.MultiValueMap<String, HttpEntity<*>> {
        val builder = MultipartBodyBuilder()
        builder.part(
            "audio",
            object : ByteArrayResource(bytes) {
                override fun getFilename(): String = filename
            }
        ).contentType(MediaType.parseMediaType(contentType))
        builder.part("merchantId", merchantId.toString())
        builder.part("branchId", branchId.toString())
        return builder.build()
    }

    private fun bearerToken(subject: String): String {
        val header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$subject"}""".toByteArray())
        return "Bearer $header.$payload.signature"
    }
}
