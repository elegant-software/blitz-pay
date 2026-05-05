package com.elegant.software.blitzpay.voice.service

import com.elegant.software.blitzpay.merchant.api.CatalogProduct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

interface ProductIntentExtractor {
    fun extract(transcript: String, catalog: List<CatalogProduct>): ProductIntent
}

@Service
class HeuristicProductIntentExtractor : ProductIntentExtractor {
    private val log = LoggerFactory.getLogger(HeuristicProductIntentExtractor::class.java)

    override fun extract(transcript: String, catalog: List<CatalogProduct>): ProductIntent {
        val normalizedTranscript = transcript.normalizeForSearch()
        val requestedQuantity = extractQuantity(normalizedTranscript)
        if (normalizedTranscript.isBlank() || catalog.isEmpty()) {
            log.info(
                "voice product intent skipped normalizedTranscriptBlank={} catalogEmpty={} requestedQuantity={}",
                normalizedTranscript.isBlank(),
                catalog.isEmpty(),
                requestedQuantity,
            )
            return ProductIntent(emptyList(), requestedQuantity)
        }

        val scoredProducts = catalog
            .map { product ->
                val score = scoreProduct(normalizedTranscript, product)
                product to score
            }
        val rankedProducts = scoredProducts
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)
        val rankedIds = rankedProducts
            .map { it.first.productId }
            .distinct()

        log.info(
            "voice product intent ranked transcript=\"{}\" requestedQuantity={} topCandidates={}",
            normalizedTranscript.take(120),
            requestedQuantity,
            rankedProducts.map { (product, score) -> "${product.productId}:${product.name.take(32)}:$score" }
        )

        return ProductIntent(
            matchedProductIds = rankedIds,
            requestedQuantity = requestedQuantity,
        )
    }

    private fun scoreProduct(transcript: String, product: CatalogProduct): Int {
        val normalizedName = product.name.normalizeForSearch()
        val normalizedDescription = product.description?.normalizeForSearch().orEmpty()

        if (normalizedName.isBlank()) return 0
        if (transcript.contains(normalizedName)) return 10_000 + normalizedName.length

        val transcriptTokens = transcript.tokenize()
        val nameTokens = normalizedName.tokenize()
        val descriptionTokens = normalizedDescription.tokenize()
        val nameMatches = nameTokens.count(transcriptTokens::contains)
        val descriptionMatches = descriptionTokens.count(transcriptTokens::contains)

        return (nameMatches * 100) + (descriptionMatches * 10)
    }

    private fun extractQuantity(transcript: String): Int? {
        val numberMatch = NUMBER_REGEX.find(transcript)?.groupValues?.get(1)?.toIntOrNull()
        if (numberMatch != null && numberMatch > 0) return numberMatch

        return WORD_NUMBERS.entries
            .firstOrNull { (word, _) -> transcript.contains("\\b$word\\b".toRegex()) }
            ?.value
    }

    private fun String.normalizeForSearch(): String =
        lowercase()
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun String.tokenize(): Set<String> =
        split(' ')
            .asSequence()
            .map(String::trim)
            .filter { it.length >= 3 }
            .toSet()

    private companion object {
        val NUMBER_REGEX = "\\b(\\d{1,3})\\b".toRegex()

        val WORD_NUMBERS = linkedMapOf(
            "one" to 1,
            "two" to 2,
            "three" to 3,
            "four" to 4,
            "five" to 5,
            "six" to 6,
            "seven" to 7,
            "eight" to 8,
            "nine" to 9,
            "ten" to 10,
            "a" to 1,
            "an" to 1,
        )
    }
}
