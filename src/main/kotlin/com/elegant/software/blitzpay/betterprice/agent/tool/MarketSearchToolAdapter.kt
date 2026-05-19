package com.elegant.software.blitzpay.betterprice.agent.tool

import com.elegant.software.blitzpay.betterprice.agent.api.PriceComparisonChatRequest
import com.elegant.software.blitzpay.betterprice.search.api.MarketSearchGateway
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchInput
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchResult
import org.springframework.stereotype.Component

@Component
class MarketSearchToolAdapter(
    private val marketSearchGateway: MarketSearchGateway
) {

    fun search(request: PriceComparisonChatRequest): MarketSearchResult =
        marketSearchGateway.search(
            MarketSearchInput(
                productTitle = request.productTitle,
                brandName = request.brandName,
                modelName = request.modelName,
                sku = request.sku,
                currency = request.currency,
                additionalAttributes = request.additionalAttributes
            )
        )
}
