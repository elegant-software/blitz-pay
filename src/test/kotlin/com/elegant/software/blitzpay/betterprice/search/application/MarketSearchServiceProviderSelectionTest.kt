package com.elegant.software.blitzpay.betterprice.search.application

import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchInput
import com.elegant.software.blitzpay.betterprice.search.parser.RetailerStructuredDataParser
import com.elegant.software.blitzpay.betterprice.search.provider.BrowserbaseFetchClient
import com.elegant.software.blitzpay.betterprice.search.provider.MarketDiscoveryProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class MarketSearchServiceProviderSelectionTest {

    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val parser = RetailerStructuredDataParser(objectMapper)
    private val webClient = WebClient.builder()
        .exchangeFunction(ExchangeFunction { Mono.error(IllegalStateException("network should not be called")) })
        .build()

    @Test
    fun `search returns provider failure when configured provider is unsupported`() {
        val properties = MarketSearchProperties(enabled = true, provider = "unknown")
        val service = MarketSearchService(
            properties = properties,
            queryFactory = MarketSearchQueryFactory(properties),
            discoveryProviders = emptyList(),
            browserbaseFetchClient = BrowserbaseFetchClient(webClient, objectMapper, properties),
            parser = parser,
            webClient = webClient
        )

        val result = service.search(MarketSearchInput(productTitle = "Sony WH-1000XM5", currency = "USD"))

        assertEquals("provider_lookup_failed", result.failure?.code)
        assertEquals("Unsupported market search provider: unknown", result.failure?.message)
    }

    @Test
    fun `search returns explicit guidance when top level market search is disabled`() {
        val properties = MarketSearchProperties(enabled = false, provider = "deepsearch")
        val service = MarketSearchService(
            properties = properties,
            queryFactory = MarketSearchQueryFactory(properties),
            discoveryProviders = emptyList(),
            browserbaseFetchClient = BrowserbaseFetchClient(webClient, objectMapper, properties),
            parser = parser,
            webClient = webClient
        )

        val result = service.search(MarketSearchInput(productTitle = "Frontline Spot On Dog S Solution", currency = "EUR"))

        assertEquals("provider_lookup_failed", result.failure?.code)
        assertEquals(
            "Market search is disabled. Set 'market-search.enabled=true'. Note: the service reads 'market-search.*', not 'market-search.koog.*'. Current provider='deepsearch'.",
            result.failure?.message
        )
    }

    @Test
    fun `search routes to configured provider name`() {
        val properties = MarketSearchProperties(enabled = true, provider = "stub")
        val provider = object : MarketDiscoveryProvider {
            override val providerName: String = "stub"

            override fun search(query: com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchQuery) =
                emptyList<com.elegant.software.blitzpay.betterprice.search.domain.SearchHit>()
        }
        val service = MarketSearchService(
            properties = properties,
            queryFactory = MarketSearchQueryFactory(properties),
            discoveryProviders = listOf(provider),
            browserbaseFetchClient = BrowserbaseFetchClient(webClient, objectMapper, properties),
            parser = parser,
            webClient = webClient
        )

        val result = service.search(MarketSearchInput(productTitle = "Sony WH-1000XM5", currency = "USD"))

        assertEquals("partial_results", result.warnings.first().code)
    }
}
