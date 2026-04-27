package com.elegant.software.blitzpay.voice.internal

import com.elegant.software.blitzpay.merchant.api.CatalogProduct
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ProductIntentExtractor(
    private val chatClient: ChatClient,
) {
    private val log = LoggerFactory.getLogger(ProductIntentExtractor::class.java)
    private val mapper = jacksonObjectMapper()

    fun extract(transcript: String, catalog: List<CatalogProduct>): ProductIntent {
        if (catalog.isEmpty()) {
            return ProductIntent(matchedProductIds = emptyList(), requestedQuantity = null)
        }

        val productListJson = mapper.writeValueAsString(
            catalog.map { p ->
                mapOf("productId" to p.productId, "name" to p.name, "description" to p.description)
            }
        )

        val prompt = """
            You are a point-of-sale assistant. The customer said: "$transcript"

            Available products (JSON array):
            $productListJson

            Instructions:
            - If the customer is asking for one or more products from the list, return a JSON object with:
              {"matches": [{"productId": "uuid", "confidence": "high|medium|low"}], "quantity": 1}
              Order matches by confidence descending. Return up to 5 matches. Set quantity from the customer's statement (default 1).
            - If the request does not match any product, return:
              {"matches": [], "quantity": null}
            - Return ONLY valid JSON. No explanation, no markdown, no extra text.
        """.trimIndent()

        val raw = try {
            chatClient.prompt(prompt).call().content() ?: ""
        } catch (ex: Exception) {
            log.warn("Ollama call failed: {}", ex.message)
            throw UpstreamAiException("Product reasoning is temporarily unavailable.")
        }

        log.debug("Ollama raw response: {}", raw)

        return try {
            val response = mapper.readValue<OllamaIntentResponse>(raw.trim())
            val ids = response.matches
                .take(5)
                .mapNotNull { match ->
                    runCatching { UUID.fromString(match.productId) }.getOrNull()
                }
            ProductIntent(matchedProductIds = ids, requestedQuantity = response.quantity)
        } catch (ex: Exception) {
            log.warn("Failed to parse Ollama intent response: {} raw={}", ex.message, raw)
            throw UpstreamAiException("Product reasoning returned an unexpected response.")
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class OllamaIntentResponse(
        val matches: List<OllamaMatch> = emptyList(),
        val quantity: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class OllamaMatch(
        val productId: String,
        val confidence: String = "low",
    )
}
