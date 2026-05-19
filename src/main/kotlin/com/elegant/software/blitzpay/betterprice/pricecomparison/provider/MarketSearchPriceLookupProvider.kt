package com.elegant.software.blitzpay.betterprice.pricecomparison.provider

import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ProductLookupRequest
import com.elegant.software.blitzpay.betterprice.search.api.MarketSearchGateway
import com.elegant.software.blitzpay.betterprice.search.domain.ExtractedMarketOffer
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchInput
import org.springframework.stereotype.Component

@Component
class MarketSearchPriceLookupProvider(
    private val marketSearchGateway: MarketSearchGateway
) : PriceLookupProvider {

    override fun findOffers(request: ProductLookupRequest): ProviderLookupResult {
        val result = marketSearchGateway.search(
            MarketSearchInput(
                productTitle = request.productTitle,
                brandName = request.brandName,
                modelName = request.modelName,
                sku = request.sku,
                currency = request.currency,
                additionalAttributes = request.additionalAttributes
            )
        )

        return ProviderLookupResult(
            offers = result.offers.map(::toProviderOffer),
            warnings = result.warnings.map { warning ->
                ProviderLookupWarning(
                    code = warning.code,
                    detail = warning.detail,
                    stage = warning.stage?.wireValue()
                )
            },
            bottleneck = result.bottleneck?.let { bottleneck ->
                ProviderLookupBottleneck(
                    stage = bottleneck.stage.wireValue(),
                    reason = bottleneck.reason,
                    detail = bottleneck.detail
                )
            },
            failure = result.failure?.let { failure ->
                ProviderLookupFailure(
                    stage = failure.stage.wireValue(),
                    code = failure.code,
                    message = failure.message,
                    retriable = failure.retriable
                )
            }
        )
    }

    private fun toProviderOffer(offer: ExtractedMarketOffer): ProviderProductOffer = ProviderProductOffer(
        normalizedTitle = offer.normalizedTitle,
        brandName = offer.brandName,
        modelName = offer.modelName,
        sku = offer.sku,
        sellerName = offer.sellerName,
        offerPrice = offer.offerPrice,
        currency = offer.currency,
        productUrl = offer.productUrl,
        available = offer.availability
    )
}
