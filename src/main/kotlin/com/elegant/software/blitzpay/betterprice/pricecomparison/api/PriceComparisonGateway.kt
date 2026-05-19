package com.elegant.software.blitzpay.betterprice.pricecomparison.api

import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.PriceComparisonResult
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ProductLookupRequest
import org.springframework.modulith.NamedInterface

@NamedInterface("PriceComparisonGateway")
interface PriceComparisonGateway {
    // The KOOG orchestration layer uses this gateway as the authoritative comparison tool entrypoint.
    fun compare(request: ProductLookupRequest): PriceComparisonResult
}
