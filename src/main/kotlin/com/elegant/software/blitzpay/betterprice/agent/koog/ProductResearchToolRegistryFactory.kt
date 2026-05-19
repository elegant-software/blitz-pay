package com.elegant.software.blitzpay.betterprice.agent.koog

import com.elegant.software.blitzpay.betterprice.agent.tool.MarketSearchToolAdapter
import com.elegant.software.blitzpay.betterprice.agent.tool.PriceComparisonToolAdapter
import org.springframework.stereotype.Component

@Component
class ProductResearchToolRegistryFactory(
    private val marketSearchToolAdapter: MarketSearchToolAdapter,
    private val priceComparisonToolAdapter: PriceComparisonToolAdapter
) {

    fun create(): ProductResearchToolRegistry = ProductResearchToolRegistry(
        marketSearchToolAdapter = marketSearchToolAdapter,
        priceComparisonToolAdapter = priceComparisonToolAdapter
    )
}

data class ProductResearchToolRegistry(
    val marketSearchToolAdapter: MarketSearchToolAdapter,
    val priceComparisonToolAdapter: PriceComparisonToolAdapter
)
