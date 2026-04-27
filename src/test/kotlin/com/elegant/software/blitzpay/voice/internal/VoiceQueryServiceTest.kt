package com.elegant.software.blitzpay.voice.internal

import com.elegant.software.blitzpay.merchant.api.CatalogProduct
import com.elegant.software.blitzpay.merchant.api.MerchantProductCatalogGateway
import com.elegant.software.blitzpay.voice.api.AssistantResponse
import com.elegant.software.blitzpay.voice.config.VoiceProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VoiceQueryServiceTest {
    private val transcriptionClient = mock<SpeechTranscriptionClient>()
    private val productCatalogGateway = mock<MerchantProductCatalogGateway>()
    private val productIntentExtractor = mock<ProductIntentExtractor>()
    private val productCatalogSearch = mock<ProductCatalogSearch>()
    private val properties = VoiceProperties()

    private val service = VoiceQueryService(
        transcriptionClient = transcriptionClient,
        properties = properties,
        productCatalogGateway = productCatalogGateway,
        productIntentExtractor = productIntentExtractor,
        productCatalogSearch = productCatalogSearch,
    )

    private val submissionNoMerchant = VoiceAudioSubmission(
        bytes = "audio".toByteArray(),
        contentType = "audio/mp4",
        callerSubject = "user-123",
    )

    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    private val submissionWithMerchant = VoiceAudioSubmission(
        bytes = "audio".toByteArray(),
        contentType = "audio/mp4",
        callerSubject = "user-123",
        merchantId = merchantId,
        branchId = branchId,
    )

    @Test
    fun `process returns TRANSCRIPT when no merchant context`() {
        whenever(transcriptionClient.transcribe(submissionNoMerchant)).thenReturn(
            VoiceTranscription(text = "What is my latest payment?", language = "en", durationSeconds = BigDecimal("2.5"))
        )

        val result = service.process(submissionNoMerchant)

        val transcript = assertIs<AssistantResponse.Transcript>(result)
        assertEquals("What is my latest payment?", transcript.transcript)
        assertEquals("en", transcript.language)
        verify(productCatalogGateway, never()).findActiveProducts(any(), any())
    }

    @Test
    fun `process returns PRODUCT_RESULT when merchant context present and product matched`() {
        val productId = UUID.randomUUID()
        val catalog = listOf(
            CatalogProduct(productId, branchId, "Erdbeer Becher", "Fresh strawberry cup", BigDecimal("3.50"), null)
        )
        val intent = ProductIntent(matchedProductIds = listOf(productId), requestedQuantity = 1)
        val expected = AssistantResponse.ProductResult(
            products = listOf(
                com.elegant.software.blitzpay.voice.api.ProductMatch(
                    productId, branchId, "Erdbeer Becher", "Fresh strawberry cup", BigDecimal("3.50"), null
                )
            ),
            requestedQuantity = 1,
        )

        whenever(transcriptionClient.transcribe(submissionWithMerchant)).thenReturn(
            VoiceTranscription(text = "I would like one Erdbeer Becher please", durationSeconds = BigDecimal("3.0"))
        )
        whenever(productCatalogGateway.findActiveProducts(merchantId, branchId)).thenReturn(catalog)
        whenever(productIntentExtractor.extract(any(), eq(catalog))).thenReturn(intent)
        whenever(productCatalogSearch.search(intent, catalog)).thenReturn(expected)

        val result = service.process(submissionWithMerchant)

        assertIs<AssistantResponse.ProductResult>(result)
        assertEquals(expected, result)
    }

    @Test
    fun `process returns NO_MATCH when merchant context present but no product matched`() {
        val catalog = listOf(
            CatalogProduct(UUID.randomUUID(), branchId, "Erdbeer Becher", null, BigDecimal("3.50"), null)
        )
        val intent = ProductIntent(matchedProductIds = emptyList(), requestedQuantity = null)
        val expected = AssistantResponse.NoMatch("I didn't understand that request — try browsing the product screen.")

        whenever(transcriptionClient.transcribe(submissionWithMerchant)).thenReturn(
            VoiceTranscription(text = "order me a pizza", durationSeconds = BigDecimal("2.0"))
        )
        whenever(productCatalogGateway.findActiveProducts(merchantId, branchId)).thenReturn(catalog)
        whenever(productIntentExtractor.extract(any(), eq(catalog))).thenReturn(intent)
        whenever(productCatalogSearch.search(intent, catalog)).thenReturn(expected)

        val result = service.process(submissionWithMerchant)

        assertIs<AssistantResponse.NoMatch>(result)
    }

    @Test
    fun `process rejects too short audio`() {
        whenever(transcriptionClient.transcribe(submissionNoMerchant)).thenReturn(
            VoiceTranscription(text = "payment", durationSeconds = BigDecimal("0.5"))
        )

        assertThrows<AudioTooShortException> { service.process(submissionNoMerchant) }
    }

    @Test
    fun `process rejects too long audio`() {
        whenever(transcriptionClient.transcribe(submissionNoMerchant)).thenReturn(
            VoiceTranscription(text = "payment", durationSeconds = BigDecimal("61"))
        )

        assertThrows<AudioTooLongException> { service.process(submissionNoMerchant) }
    }

    @Test
    fun `process propagates upstream transcription failure`() {
        whenever(transcriptionClient.transcribe(submissionNoMerchant)).thenThrow(
            UpstreamAiException("Voice transcription is temporarily unavailable.")
        )

        assertThrows<UpstreamAiException> { service.process(submissionNoMerchant) }
    }
}
