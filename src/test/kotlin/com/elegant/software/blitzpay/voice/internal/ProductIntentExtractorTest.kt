package com.elegant.software.blitzpay.voice.internal

import com.elegant.software.blitzpay.merchant.api.CatalogProduct
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductIntentExtractorTest {
    private val chatClient = mock<ChatClient>()
    private val requestSpec = mock<ChatClientRequestSpec>()
    private val responseSpec = mock<CallResponseSpec>()

    private val extractor = ProductIntentExtractor(chatClient)

    private val productId1 = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val productId2 = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val branchId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    private val catalog = listOf(
        CatalogProduct(productId1, branchId, "Erdbeer Becher", "Fresh strawberry cup", BigDecimal("3.50"), null),
        CatalogProduct(productId2, branchId, "Erdbeer Shake", "Strawberry milkshake", BigDecimal("4.20"), null),
    )

    private fun stubOllamaResponse(json: String) {
        whenever(chatClient.prompt(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenReturn(responseSpec)
        whenever(responseSpec.content()).thenReturn(json)
    }

    @Test
    fun `extract returns single high-confidence match for exact product name`() {
        stubOllamaResponse("""{"matches":[{"productId":"$productId1","confidence":"high"}],"quantity":1}""")

        val intent = extractor.extract("I would like one Erdbeer Becher please", catalog)

        assertEquals(listOf(productId1), intent.matchedProductIds)
        assertEquals(1, intent.requestedQuantity)
    }

    @Test
    fun `extract returns multiple matches for ambiguous query`() {
        stubOllamaResponse("""{"matches":[{"productId":"$productId1","confidence":"medium"},{"productId":"$productId2","confidence":"low"}],"quantity":null}""")

        val intent = extractor.extract("something strawberry", catalog)

        assertEquals(listOf(productId1, productId2), intent.matchedProductIds)
        assertNull(intent.requestedQuantity)
    }

    @Test
    fun `extract caps matches at 5`() {
        val ids = (1..6).map { UUID.randomUUID() }
        val matchesJson = ids.joinToString(",") { """{"productId":"$it","confidence":"low"}""" }
        stubOllamaResponse("""{"matches":[$matchesJson],"quantity":null}""")

        val bigCatalog = ids.map { CatalogProduct(it, branchId, "Product $it", null, BigDecimal.ONE, null) }
        val intent = extractor.extract("give me something", bigCatalog)

        assertTrue(intent.matchedProductIds.size <= 5)
    }

    @Test
    fun `extract returns empty intent when no match`() {
        stubOllamaResponse("""{"matches":[],"quantity":null}""")

        val intent = extractor.extract("order me a pizza", catalog)

        assertTrue(intent.matchedProductIds.isEmpty())
        assertNull(intent.requestedQuantity)
    }

    @Test
    fun `extract returns empty intent for empty catalog`() {
        val intent = extractor.extract("I would like an Erdbeer Becher", emptyList())

        assertTrue(intent.matchedProductIds.isEmpty())
    }

    @Test
    fun `extract throws UpstreamAiException on Ollama failure`() {
        whenever(chatClient.prompt(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.call()).thenThrow(RuntimeException("connection refused"))

        assertThrows<UpstreamAiException> {
            extractor.extract("I would like one Erdbeer Becher", catalog)
        }
    }

    @Test
    fun `extract throws UpstreamAiException on malformed JSON`() {
        stubOllamaResponse("not valid json at all")

        assertThrows<UpstreamAiException> {
            extractor.extract("I would like one Erdbeer Becher", catalog)
        }
    }
}
