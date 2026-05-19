package com.elegant.software.blitzpay.betterprice.search.api

import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchInput
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchResult
import org.springframework.modulith.NamedInterface

@NamedInterface("MarketSearchGateway")
interface MarketSearchGateway {
    // The KOOG orchestration layer uses this gateway as the bounded market-search tool entrypoint.
    fun search(input: com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchInput): com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchResult
}
