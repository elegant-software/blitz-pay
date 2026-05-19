package com.elegant.software.blitzpay.betterprice.pricecomparison.application

import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ComparableOffer
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MatchConfidence
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MatchedProduct
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.OfferQualificationStatus
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ProductLookupRequest
import com.elegant.software.blitzpay.betterprice.pricecomparison.provider.ProviderProductOffer
import org.springframework.stereotype.Component

@Component
class ProductMatchingService {

    fun resolve(request: ProductLookupRequest, offers: List<ProviderProductOffer>): ProductMatchResolution {
        if (offers.isEmpty()) {
            return ProductMatchResolution(null, emptyList(), emptyList(), MatchConfidence.LOW)
        }

        val scored = offers.map { offer -> offer to score(request, offer) }
        val bestScore = scored.maxOf { it.second }
        val bestOffer = scored.first { it.second == bestScore }.first
        val confidence = toConfidence(bestScore)

        val considered = mutableListOf<ComparableOffer>()
        val excluded = mutableListOf<ComparableOffer>()

        scored.forEach { (offer, score) ->
            val comparableOffer = toComparableOffer(
                offer = offer,
                qualificationStatus = when {
                    !offer.available -> OfferQualificationStatus.UNAVAILABLE
                    score < bestScore -> OfferQualificationStatus.WEAK_MATCH
                    else -> OfferQualificationStatus.QUALIFIED
                },
                qualificationNotes = when {
                    !offer.available -> "Offer is not currently available"
                    score < bestScore -> "Offer was excluded because product identity match was weaker than the best match"
                    else -> "Offer matched the requested product strongly enough to be compared"
                }
            )

            if (!offer.available || score < bestScore) {
                excluded += comparableOffer
            } else {
                considered += comparableOffer
            }
        }

        return ProductMatchResolution(
            matchedProduct = MatchedProduct(
                normalizedTitle = bestOffer.normalizedTitle,
                brandName = bestOffer.brandName,
                modelName = bestOffer.modelName,
                sku = bestOffer.sku,
                matchConfidence = confidence,
                matchEvidence = buildEvidence(request, bestOffer)
            ),
            consideredOffers = considered,
            excludedOffers = excluded,
            confidence = confidence
        )
    }

    private fun toComparableOffer(
        offer: ProviderProductOffer,
        qualificationStatus: OfferQualificationStatus,
        qualificationNotes: String
    ): ComparableOffer = ComparableOffer(
        sellerName = offer.sellerName,
        offerPrice = offer.offerPrice,
        currency = offer.currency,
        productUrl = offer.productUrl,
        availability = offer.available,
        qualificationStatus = qualificationStatus,
        qualificationNotes = qualificationNotes
    )

    private fun buildEvidence(request: ProductLookupRequest, offer: ProviderProductOffer): List<String> = buildList {
        if (!request.brandName.isNullOrBlank() && request.brandName.equals(offer.brandName, ignoreCase = true)) {
            add("brand")
        }
        if (!request.modelName.isNullOrBlank() && request.modelName.equals(offer.modelName, ignoreCase = true)) {
            add("model")
        }
        if (!request.sku.isNullOrBlank() && request.sku.equals(offer.sku, ignoreCase = true)) {
            add("sku")
        }
        if (!request.productTitle.isNullOrBlank() && normalize(offer.normalizedTitle).contains(normalize(request.productTitle))) {
            add("title")
        }
    }.ifEmpty { listOf("title") }

    private fun score(request: ProductLookupRequest, offer: ProviderProductOffer): Int {
        var score = 0
        if (!request.sku.isNullOrBlank() && request.sku.equals(offer.sku, ignoreCase = true)) score += 4
        if (!request.brandName.isNullOrBlank() && request.brandName.equals(offer.brandName, ignoreCase = true)) score += 2
        if (!request.modelName.isNullOrBlank() && request.modelName.equals(offer.modelName, ignoreCase = true)) score += 2
        if (!request.productTitle.isNullOrBlank()) {
            val normalizedTitle = normalize(request.productTitle)
            if (normalize(offer.normalizedTitle).contains(normalizedTitle) || normalizedTitle.contains(normalize(offer.normalizedTitle))) {
                score += 1
            }
        }
        return score
    }

    private fun toConfidence(score: Int): MatchConfidence = when {
        score >= 4 -> MatchConfidence.HIGH
        score >= 2 -> MatchConfidence.MEDIUM
        else -> MatchConfidence.LOW
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}

data class ProductMatchResolution(
    val matchedProduct: MatchedProduct?,
    val consideredOffers: List<ComparableOffer>,
    val excludedOffers: List<ComparableOffer>,
    val confidence: MatchConfidence
)
