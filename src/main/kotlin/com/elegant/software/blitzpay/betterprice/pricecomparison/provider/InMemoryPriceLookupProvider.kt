package com.elegant.software.blitzpay.betterprice.pricecomparison.provider

import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ProductLookupRequest
import java.math.BigDecimal

class InMemoryPriceLookupProvider : PriceLookupProvider {

    private val offers = listOf(
        ProviderProductOffer(
            normalizedTitle = "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
            brandName = "Sony",
            modelName = "WH-1000XM5",
            sku = "SONY-WH1000XM5-BLK",
            sellerName = "TechHub",
            offerPrice = BigDecimal("279.99"),
            currency = "USD",
            productUrl = "https://example.test/offers/sony-wh1000xm5-techhub"
        ),
        ProviderProductOffer(
            normalizedTitle = "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
            brandName = "Sony",
            modelName = "WH-1000XM5",
            sku = "SONY-WH1000XM5-BLK",
            sellerName = "AudioDeals",
            offerPrice = BigDecimal("299.99"),
            currency = "USD",
            productUrl = "https://example.test/offers/sony-wh1000xm5-audiodeals"
        ),
        ProviderProductOffer(
            normalizedTitle = "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
            brandName = "Sony",
            modelName = "WH-1000XM5",
            sku = "SONY-WH1000XM5-BLK",
            sellerName = "LocalElectronics",
            offerPrice = BigDecimal("339.99"),
            currency = "USD",
            productUrl = "https://example.test/offers/sony-wh1000xm5-local"
        ),
        ProviderProductOffer(
            normalizedTitle = "Dyson Airwrap Multi-Styler Complete Long",
            brandName = "Dyson",
            modelName = "Airwrap Complete Long",
            sku = "DYS-AIRWRAP-COMPLETE-LONG",
            sellerName = "BeautyWorld",
            offerPrice = BigDecimal("599.99"),
            currency = "USD",
            productUrl = "https://example.test/offers/dyson-airwrap-beautyworld"
        ),
        ProviderProductOffer(
            normalizedTitle = "Dyson Airwrap Multi-Styler Complete Long",
            brandName = "Dyson",
            modelName = "Airwrap Complete Long",
            sku = "DYS-AIRWRAP-COMPLETE-LONG",
            sellerName = "StyleMarket",
            offerPrice = BigDecimal("629.99"),
            currency = "USD",
            productUrl = "https://example.test/offers/dyson-airwrap-stylemarket"
        ),
        ProviderProductOffer(
            normalizedTitle = "Dyson Airwrap Multi-Styler Complete Long",
            brandName = "Dyson",
            modelName = "Airwrap Complete Long",
            sku = "DYS-AIRWRAP-COMPLETE-LONG",
            sellerName = "OutletHair",
            offerPrice = BigDecimal("579.99"),
            currency = "USD",
            productUrl = "https://example.test/offers/dyson-airwrap-outlet",
            available = false
        )
    )

    override fun findOffers(request: ProductLookupRequest): ProviderLookupResult {
        val tokens = buildList {
            request.productTitle?.let(::add)
            request.brandName?.let(::add)
            request.modelName?.let(::add)
            request.sku?.let(::add)
            addAll(request.additionalAttributes.values)
        }.map(::normalize)
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) {
            return ProviderLookupResult(emptyList())
        }

        val matches = offers.filter { offer ->
            val candidate = listOfNotNull(
                offer.normalizedTitle,
                offer.brandName,
                offer.modelName,
                offer.sku
            ).joinToString(" ") { normalize(it) }

            tokens.any { token ->
                candidate.contains(token) || token.contains(candidate)
            }
        }

        val mode = request.additionalAttributes["monitoringMode"]?.lowercase()
        if (mode == "lookup_failure") {
            return ProviderLookupResult(
                offers = emptyList(),
                warnings = listOf(
                    ProviderLookupWarning(
                        code = "partial_results",
                        detail = "Lookup aborted before a complete retailer set could be inspected",
                        stage = "search_discovery"
                    )
                ),
                bottleneck = ProviderLookupBottleneck(
                    stage = "search_discovery",
                    reason = "provider_timeout",
                    detail = "External retailer lookup exceeded the synchronous request budget"
                ),
                failure = ProviderLookupFailure(
                    stage = "search_discovery",
                    code = "provider_lookup_failed",
                    message = "Retailer lookup failed before a comparable offer set was assembled",
                    retriable = true
                )
            )
        }

        if (mode == "lookup_slowdown") {
            return ProviderLookupResult(
                offers = matches,
                warnings = listOf(
                    ProviderLookupWarning(
                        code = "partial_results",
                        detail = "Lookup completed with a reduced retailer set after a slow provider response",
                        stage = "offer_extraction"
                    )
                ),
                bottleneck = ProviderLookupBottleneck(
                    stage = "offer_extraction",
                    reason = "slow_provider_response",
                    detail = "Offer lookup took longer than expected while waiting for a retailer response"
                )
            )
        }

        return ProviderLookupResult(
            offers = matches,
            warnings = if (matches.any { !it.available }) {
                listOf(
                    ProviderLookupWarning(
                        code = "unavailable_offers",
                        detail = "One or more matching offers were excluded because they were unavailable",
                        stage = "offer_extraction"
                    )
                )
            } else {
                emptyList()
            }
        )
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
