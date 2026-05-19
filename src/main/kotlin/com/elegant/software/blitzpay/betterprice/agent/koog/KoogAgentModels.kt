package com.elegant.software.blitzpay.betterprice.agent.koog

import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchResult
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.PriceComparisonResult
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.PriceComparisonStatus
import java.time.Instant

data class ProductResearchKoogSession(
    val sessionId: String,
    val providerName: String,
    val systemPromptVersion: String,
    val createdAt: Instant = Instant.now()
)

data class ProductResearchKoogRun(
    val session: ProductResearchKoogSession,
    val searchPreview: MarketSearchResult?,
    val result: PriceComparisonResult
)

data class ProductResearchToolCall(
    val toolName: String,
    val successful: Boolean,
    val detail: String? = null
)

data class ProductResearchAgentTrace(
    val session: ProductResearchKoogSession,
    val toolCalls: List<ProductResearchToolCall>,
    val status: PriceComparisonStatus
)
