package com.elegant.software.blitzpay.betterprice.pricecomparison.provider

import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ProductLookupRequest
import java.math.BigDecimal

interface PriceLookupProvider {
    fun findOffers(request: ProductLookupRequest): ProviderLookupResult
}

data class ProviderLookupResult(
    val offers: List<ProviderProductOffer>,
    val warnings: List<ProviderLookupWarning> = emptyList(),
    val bottleneck: ProviderLookupBottleneck? = null,
    val failure: ProviderLookupFailure? = null
)

data class ProviderProductOffer(
    val normalizedTitle: String,
    val brandName: String? = null,
    val modelName: String? = null,
    val sku: String? = null,
    val sellerName: String,
    val offerPrice: BigDecimal,
    val currency: String,
    val productUrl: String? = null,
    val available: Boolean = true
)

data class ProviderLookupWarning(
    val code: String,
    val detail: String,
    val stage: String? = null
)

data class ProviderLookupBottleneck(
    val stage: String = "offer_lookup",
    val reason: String,
    val detail: String? = null
)

data class ProviderLookupFailure(
    val stage: String = "offer_lookup",
    val code: String,
    val message: String,
    val retriable: Boolean = false
)
