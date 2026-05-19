package com.elegant.software.blitzpay.betterprice.search.provider

import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchQuery
import com.elegant.software.blitzpay.betterprice.search.domain.SearchHit

interface MarketDiscoveryProvider {
    val providerName: String

    fun search(query: MarketSearchQuery): List<SearchHit>
}
