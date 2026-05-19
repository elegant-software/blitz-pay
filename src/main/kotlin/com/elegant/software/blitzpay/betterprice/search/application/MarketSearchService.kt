package com.elegant.software.blitzpay.betterprice.search.application

import com.elegant.software.blitzpay.betterprice.search.api.MarketSearchGateway
import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import com.elegant.software.blitzpay.betterprice.search.domain.ExtractedMarketOffer
import com.elegant.software.blitzpay.betterprice.search.domain.ExtractionEvidence
import com.elegant.software.blitzpay.betterprice.search.domain.ExtractionSourceType
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchBottleneck
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchFailure
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchInput
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchQuery
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchResult
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchStage
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchWarning
import com.elegant.software.blitzpay.betterprice.search.domain.SearchHit
import com.elegant.software.blitzpay.betterprice.search.parser.RetailerStructuredDataParser
import com.elegant.software.blitzpay.betterprice.search.provider.BrowserbaseFetchClient
import com.elegant.software.blitzpay.betterprice.search.provider.MarketDiscoveryProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.net.URI
import java.math.BigDecimal

@Service
class MarketSearchService(
    private val properties: MarketSearchProperties,
    private val queryFactory: MarketSearchQueryFactory,
    discoveryProviders: List<MarketDiscoveryProvider>,
    private val browserbaseFetchClient: BrowserbaseFetchClient,
    private val parser: RetailerStructuredDataParser,
    private val webClient: WebClient
) : MarketSearchGateway {

    private val log = LoggerFactory.getLogger(javaClass)
    private val discoveryProvidersByName = discoveryProviders.associateBy { it.providerName.lowercase() }

    override fun search(input: MarketSearchInput): MarketSearchResult {
        log.info(
            "Market search request received: enabled={}, provider={}, title='{}', brand='{}', model='{}', sku='{}'",
            properties.enabled,
            properties.provider,
            input.productTitle,
            input.brandName,
            input.modelName,
            input.sku
        )

        if (!properties.enabled) {
            log.warn(
                "Market search skipped because top-level property 'market-search.enabled' is false. Provider='{}'. Note: the service reads 'market-search.*', not 'market-search.koog.*'.",
                properties.provider
            )
            return MarketSearchResult(
                offers = emptyList(),
                failure = MarketSearchFailure(
                    stage = MarketSearchStage.SEARCH_DISCOVERY,
                    code = "provider_lookup_failed",
                    message = "Market search is disabled. Set 'market-search.enabled=true'. Note: the service reads 'market-search.*', not 'market-search.koog.*'. Current provider='${properties.provider}'.",
                    retriable = false
                )
            )
        }

        if (properties.provider.equals("fixture", ignoreCase = true)) {
            log.info("Market search executing in fixture mode")
            return fixtureResult(input)
        }

        val queries = runCatching { queryFactory.createQueries(input) }
            .getOrElse { ex ->
                log.warn("Market search query creation failed for provider='{}': {}", properties.provider, ex.message)
                return MarketSearchResult(
                    offers = emptyList(),
                    failure = MarketSearchFailure(
                        stage = MarketSearchStage.SEARCH_DISCOVERY,
                        code = "provider_lookup_failed",
                        message = ex.message ?: "Market search query could not be built",
                        retriable = false
                    )
                )
            }

        val providerName = properties.provider.lowercase()
        val discoveryProvider = discoveryProvidersByName[providerName]
            ?: return MarketSearchResult(
                offers = emptyList(),
                failure = MarketSearchFailure(
                    stage = MarketSearchStage.SEARCH_DISCOVERY,
                    code = "provider_lookup_failed",
                    message = "Unsupported market search provider: ${properties.provider}",
                    retriable = false
                )
            ).also {
                log.warn(
                    "Unsupported market search provider '{}'. Available providers={}",
                    properties.provider,
                    discoveryProvidersByName.keys.sorted()
                )
            }

        log.info(
            "Market search using provider='{}' with queryCount={}, maxHits={} and maxPagesToFetch={}",
            providerName,
            queries.size,
            properties.maxHits,
            properties.maxPagesToFetch
        )

        val hits = runCatching {
            queries.flatMap { query ->
                log.info("Running discovery query for provider='{}': {}", providerName, query.queryText)
                discoveryProvider.search(query)
            }
        }
            .getOrElse { ex ->
                log.warn("Provider '{}' search discovery failed: {}", providerName, ex.message)
                return providerFailure(
                    providerName = providerName,
                    message = ex.message ?: "Search discovery failed",
                    retriable = true
                )
            }
            .let(::dedupeAndRankHits)

        return extractOffers(
            providerName = providerName,
            query = queries.first(),
            hits = hits
        )
    }

    private fun extractOffers(
        providerName: String,
        query: MarketSearchQuery,
        hits: List<SearchHit>
    ): MarketSearchResult {
        if (hits.isEmpty()) {
            log.info("Provider '{}' returned no candidate retailer pages for query='{}'", providerName, query.queryText)
            return MarketSearchResult(
                offers = emptyList(),
                searchHits = emptyList(),
                warnings = listOf(
                    MarketSearchWarning(
                        code = "partial_results",
                        detail = "$providerName search returned no candidate retailer pages for query '${query.queryText}'",
                        stage = MarketSearchStage.SEARCH_DISCOVERY
                    )
                )
            )
        }

        val offers = mutableListOf<ExtractedMarketOffer>()
        val warnings = mutableListOf<MarketSearchWarning>()
        var bottleneck: MarketSearchBottleneck? = null

        log.info("Provider '{}' returned {} discovery hits. Fetching up to {} retailer pages.", providerName, hits.size, properties.maxPagesToFetch.coerceAtLeast(1))

        hits.take(properties.maxPagesToFetch.coerceAtLeast(1)).forEach { hit ->
            runCatching {
                fetchRetailerPage(providerName, hit.url)
            }.onSuccess { html ->
                val offer = html?.takeIf(String::isNotBlank)?.let { parser.parse(it, hit.url) }
                if (offer != null) {
                    log.info(
                        "Extracted comparable offer from provider='{}', seller='{}', url='{}'",
                        hit.providerName,
                        offer.sellerName,
                        hit.url
                    )
                    offers += offer
                } else {
                    val followUpUrls = html
                        ?.takeIf { providerName == "deepsearch" }
                        ?.let { parser.extractRetailerLinks(it, hit.url) }
                        .orEmpty()

                    if (followUpUrls.isNotEmpty()) {
                        log.info(
                            "No direct offer extracted for url='{}'. Following {} outbound retailer links: {}",
                            hit.url,
                            followUpUrls.size,
                            followUpUrls.take(3)
                        )
                    }

                    val followUpOffer = followUpUrls
                        .take(2)
                        .firstNotNullOfOrNull { followUpUrl ->
                            runCatching {
                                fetchRetailerPage(providerName, followUpUrl)
                                    ?.takeIf(String::isNotBlank)
                                    ?.let { followUpHtml -> parser.parse(followUpHtml, followUpUrl) }
                            }.getOrNull()
                        }

                    if (followUpOffer != null) {
                        log.info(
                            "Extracted comparable offer from follow-up retailer link for source='{}', seller='{}', url='{}'",
                            hit.url,
                            followUpOffer.sellerName,
                            followUpOffer.productUrl
                        )
                        offers += followUpOffer
                        return@onSuccess
                    }

                    log.warn(
                        "No extractable structured offer data for provider='{}', url='{}'. Diagnostics: {}",
                        hit.providerName,
                        hit.url,
                        html?.let { parser.diagnostics(it, hit.url) } ?: "<empty html>"
                    )
                    warnings += MarketSearchWarning(
                        code = "no_extractable_offer",
                        detail = "${hit.providerName} search hit did not expose extractable structured offer data",
                        stage = MarketSearchStage.OFFER_EXTRACTION
                    )
                }
            }.onFailure { ex ->
                warnings += MarketSearchWarning(
                    code = "partial_results",
                    detail = "${hit.providerName} retailer page fetch failed for ${hit.url}: ${ex.message}",
                    stage = MarketSearchStage.OFFER_EXTRACTION
                )
                log.warn(
                    "Retailer page fetch failed for provider='{}', url='{}': {}",
                    hit.providerName,
                    hit.url,
                    ex.message
                )
                bottleneck = MarketSearchBottleneck(
                    stage = MarketSearchStage.OFFER_EXTRACTION,
                    reason = "page_fetch_failed",
                    detail = "At least one ${hit.providerName} retailer page could not be fetched within the request budget"
                )
            }
        }

        return MarketSearchResult(
            offers = offers,
            searchHits = hits,
            warnings = warnings,
            bottleneck = bottleneck
        ).also {
            log.info(
                "Market search completed for provider='{}' with {} offers, {} warnings, bottleneckPresent={}",
                providerName,
                offers.size,
                warnings.size,
                bottleneck != null
            )
        }
    }

    private fun fetchRetailerPage(providerName: String, url: String): String? {
        if (providerName == "deepsearch") {
            val response = browserbaseFetchClient.fetch(url)
            if (response.statusCode >= 400) {
                error(
                    "Browserbase Fetch returned status=${response.statusCode}, contentType='${response.contentType}', encoding='${response.encoding}'"
                )
            }
            log.info(
                "Browserbase Fetch succeeded for url='{}' with status={}, contentType='{}', encoding='{}', contentLength={}",
                url,
                response.statusCode,
                response.contentType,
                response.encoding,
                response.content.length
            )
            log.info(
                "Browserbase Fetch content preview for url='{}': {}",
                url,
                response.content.previewForLog()
            )
            return response.content
        }

        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(String::class.java)
            .block(properties.requestTimeout)
    }

    private fun providerFailure(
        providerName: String,
        message: String,
        retriable: Boolean
    ): MarketSearchResult = MarketSearchResult(
        offers = emptyList(),
        failure = MarketSearchFailure(
            stage = MarketSearchStage.SEARCH_DISCOVERY,
            code = "provider_lookup_failed",
            message = "$providerName search failed: $message",
            retriable = retriable
        )
    ).also {
        log.warn("Market search failure recorded for provider='{}': {}", providerName, message)
    }

    private fun dedupeAndRankHits(hits: List<SearchHit>): List<SearchHit> = hits
        .distinctBy { it.url.lowercase() }
        .sortedWith(
            compareBy<SearchHit> { it.manufacturerPenalty() }
                .thenBy { it.rank }
        )
        .mapIndexed { index, hit -> hit.copy(rank = index + 1) }

    private fun SearchHit.manufacturerPenalty(): Int {
        val host = runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
        val titleText = title.lowercase()
        val snippetText = snippet.orEmpty().lowercase()
        val looksInformational = host.endsWith(".brand") ||
            host.contains("frontline") ||
            titleText.contains("official") ||
            snippetText.contains("official") ||
            snippetText.contains("learn more") ||
            snippetText.contains("flea and tick protection")

        return if (looksInformational) 1 else 0
    }

    private fun fixtureResult(input: MarketSearchInput): MarketSearchResult {
        val monitoringMode = input.additionalAttributes["monitoringMode"]?.lowercase()

        if (monitoringMode == "lookup_failure") {
            return MarketSearchResult(
                offers = emptyList(),
                warnings = listOf(
                    MarketSearchWarning(
                        code = "partial_results",
                        detail = "Lookup aborted before a complete retailer set could be inspected",
                        stage = MarketSearchStage.SEARCH_DISCOVERY
                    )
                ),
                bottleneck = MarketSearchBottleneck(
                    stage = MarketSearchStage.SEARCH_DISCOVERY,
                    reason = "provider_timeout",
                    detail = "External retailer lookup exceeded the synchronous request budget"
                ),
                failure = MarketSearchFailure(
                    stage = MarketSearchStage.SEARCH_DISCOVERY,
                    code = "provider_lookup_failed",
                    message = "Retailer lookup failed before a comparable offer set was assembled",
                    retriable = true
                )
            )
        }

        val offers = when {
            input.sku.equals("SONY-WH1000XM5-BLK", ignoreCase = true) -> sonyOffers()
            input.sku.equals("DYS-AIRWRAP-COMPLETE-LONG", ignoreCase = true) -> dysonOffers()
            input.productTitle.equals("Wireless Earbuds", ignoreCase = true) -> genericEarbudsOffers()
            else -> emptyList()
        }

        val warnings = mutableListOf<MarketSearchWarning>()
        var bottleneck: MarketSearchBottleneck? = null
        if (monitoringMode == "lookup_slowdown") {
            warnings += MarketSearchWarning(
                code = "partial_results",
                detail = "Lookup completed with a reduced retailer set after a slow provider response",
                stage = MarketSearchStage.OFFER_EXTRACTION
            )
            bottleneck = MarketSearchBottleneck(
                stage = MarketSearchStage.OFFER_EXTRACTION,
                reason = "slow_provider_response",
                detail = "Offer extraction took longer than expected while processing retailer responses"
            )
        }

        return MarketSearchResult(
            offers = offers,
            warnings = warnings,
            bottleneck = bottleneck
        )
    }

    private fun sonyOffers(): List<ExtractedMarketOffer> = listOf(
        fixtureOffer(
            sellerName = "TechHub",
            offerPrice = "279.99",
            productUrl = "https://example.test/offers/sony-wh1000xm5-techhub",
            normalizedTitle = "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
            brandName = "Sony",
            modelName = "WH-1000XM5",
            sku = "SONY-WH1000XM5-BLK"
        ),
        fixtureOffer(
            sellerName = "AudioDeals",
            offerPrice = "299.99",
            productUrl = "https://example.test/offers/sony-wh1000xm5-audiodeals",
            normalizedTitle = "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
            brandName = "Sony",
            modelName = "WH-1000XM5",
            sku = "SONY-WH1000XM5-BLK"
        )
    )

    private fun dysonOffers(): List<ExtractedMarketOffer> = listOf(
        fixtureOffer(
            sellerName = "BeautyWorld",
            offerPrice = "599.99",
            productUrl = "https://example.test/offers/dyson-airwrap-beautyworld",
            normalizedTitle = "Dyson Airwrap Multi-Styler Complete Long",
            brandName = "Dyson",
            modelName = "Airwrap Complete Long",
            sku = "DYS-AIRWRAP-COMPLETE-LONG"
        ),
        fixtureOffer(
            sellerName = "StyleMarket",
            offerPrice = "629.99",
            productUrl = "https://example.test/offers/dyson-airwrap-stylemarket",
            normalizedTitle = "Dyson Airwrap Multi-Styler Complete Long",
            brandName = "Dyson",
            modelName = "Airwrap Complete Long",
            sku = "DYS-AIRWRAP-COMPLETE-LONG"
        )
    )

    private fun genericEarbudsOffers(): List<ExtractedMarketOffer> = listOf(
        fixtureOffer(
            sellerName = "MarketplaceA",
            offerPrice = "59.99",
            productUrl = "https://example.test/offers/generic-earbuds-a",
            normalizedTitle = "Generic Wireless Earbuds"
        ),
        fixtureOffer(
            sellerName = "MarketplaceB",
            offerPrice = "49.99",
            productUrl = "https://example.test/offers/generic-earbuds-b",
            normalizedTitle = "Budget Bluetooth Earbuds"
        )
    )

    private fun fixtureOffer(
        sellerName: String,
        offerPrice: String,
        productUrl: String,
        normalizedTitle: String,
        brandName: String? = null,
        modelName: String? = null,
        sku: String? = null
    ): ExtractedMarketOffer = ExtractedMarketOffer(
        sellerName = sellerName,
        offerPrice = BigDecimal(offerPrice),
        currency = "USD",
        availability = true,
        productUrl = productUrl,
        normalizedTitle = normalizedTitle,
        brandName = brandName,
        modelName = modelName,
        sku = sku,
        extractionEvidence = ExtractionEvidence(
            sourceType = ExtractionSourceType.JSON_LD,
            observedFields = listOf("name", "price", "priceCurrency"),
            pageTitle = normalizedTitle,
            sellerHint = sellerName
        )
    )
}

private fun String.previewForLog(maxLength: Int = 500): String =
    replace(Regex("\\s+"), " ").trim().let { compact ->
        if (compact.length <= maxLength) compact else compact.take(maxLength) + "...[truncated]"
    }
