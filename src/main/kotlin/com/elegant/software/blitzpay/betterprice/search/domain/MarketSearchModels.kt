package com.elegant.software.blitzpay.betterprice.search.domain

import java.math.BigDecimal

data class MarketSearchInput(
    val productTitle: String? = null,
    val brandName: String? = null,
    val modelName: String? = null,
    val sku: String? = null,
    val currency: String,
    val additionalAttributes: Map<String, String> = emptyMap()
)

data class MarketSearchQuery(
    val queryText: String,
    val brandName: String? = null,
    val modelName: String? = null,
    val sku: String? = null,
    val currency: String,
    val maxHits: Int
)

data class SearchHit(
    val title: String,
    val url: String,
    val displayUrl: String? = null,
    val snippet: String? = null,
    val rank: Int,
    val providerName: String
)

data class ExtractionEvidence(
    val sourceType: ExtractionSourceType,
    val observedFields: List<String>,
    val pageTitle: String? = null,
    val sellerHint: String? = null
)

data class ExtractedMarketOffer(
    val sellerName: String,
    val offerPrice: BigDecimal,
    val currency: String,
    val availability: Boolean,
    val productUrl: String,
    val normalizedTitle: String,
    val brandName: String? = null,
    val modelName: String? = null,
    val sku: String? = null,
    val extractionEvidence: ExtractionEvidence
)

data class MarketSearchWarning(
    val code: String,
    val detail: String,
    val stage: MarketSearchStage? = null
)

data class MarketSearchBottleneck(
    val stage: MarketSearchStage,
    val reason: String,
    val detail: String? = null
)

data class MarketSearchFailure(
    val stage: MarketSearchStage,
    val code: String,
    val message: String,
    val retriable: Boolean = false
)

data class MarketSearchResult(
    val offers: List<ExtractedMarketOffer>,
    val searchHits: List<SearchHit> = emptyList(),
    val warnings: List<MarketSearchWarning> = emptyList(),
    val bottleneck: MarketSearchBottleneck? = null,
    val failure: MarketSearchFailure? = null
)
