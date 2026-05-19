package com.elegant.software.blitzpay.betterprice.search.application

import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchInput
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchQuery
import org.springframework.stereotype.Component

@Component
class MarketSearchQueryFactory(
    private val properties: MarketSearchProperties
) {

    fun create(input: MarketSearchInput): MarketSearchQuery {
        return createQueries(input).first()
    }

    fun createQueries(input: MarketSearchInput): List<MarketSearchQuery> {
        val parts = buildList {
            input.brandName?.takeIf(String::isNotBlank)?.let(::add)
            input.modelName?.takeIf(String::isNotBlank)?.let(::add)
            input.productTitle?.takeIf(String::isNotBlank)?.let(::add)
            input.sku?.takeIf(String::isNotBlank)?.let(::add)
        }

        val baseQueryText = parts.joinToString(" ").trim()
        require(baseQueryText.isNotBlank()) { "market search query must not be blank" }

        val maxHits = properties.maxHits.coerceAtLeast(1)
        val shoppingQueries = buildList {
            add(baseQueryText)
            add("$baseQueryText price ${input.currency.trim()}")
            add("buy $baseQueryText")
            add("$baseQueryText retailer")
            add("$baseQueryText shop")
        }.distinct()

        return shoppingQueries.map { queryText ->
            MarketSearchQuery(
                queryText = queryText,
                brandName = input.brandName?.trim(),
                modelName = input.modelName?.trim(),
                sku = input.sku?.trim(),
                currency = input.currency.trim(),
                maxHits = maxHits
            )
        }
    }
}
