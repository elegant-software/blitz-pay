package com.elegant.software.blitzpay.betterprice.search.application

import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchInput
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchStage
import com.elegant.software.blitzpay.betterprice.search.provider.BraveSearchClient
import com.elegant.software.blitzpay.betterprice.search.provider.BrowserbaseFetchClient
import com.elegant.software.blitzpay.betterprice.search.parser.RetailerStructuredDataParser
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class MarketSearchMonitoringTest {

    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val properties = MarketSearchProperties(enabled = true, provider = "fixture")
    private val webClient = WebClient.builder()
        .exchangeFunction(ExchangeFunction { Mono.error(IllegalStateException("network should not be called")) })
        .build()
    private val service = MarketSearchService(
        properties = properties,
        queryFactory = MarketSearchQueryFactory(properties),
        discoveryProviders = listOf(BraveSearchClient(webClient, objectMapper, properties)),
        browserbaseFetchClient = BrowserbaseFetchClient(webClient, objectMapper, properties),
        parser = RetailerStructuredDataParser(objectMapper),
        webClient = webClient
    )

    @Test
    fun `fixture slowdown adds offer extraction bottleneck`() {
        val result = service.search(
            MarketSearchInput(
                productTitle = "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
                brandName = "Sony",
                modelName = "WH-1000XM5",
                sku = "SONY-WH1000XM5-BLK",
                currency = "USD",
                additionalAttributes = mapOf("monitoringMode" to "lookup_slowdown")
            )
        )

        assertEquals(MarketSearchStage.OFFER_EXTRACTION, result.bottleneck?.stage)
        assertEquals("partial_results", result.warnings.first().code)
    }

    @Test
    fun `fixture failure adds search discovery failure`() {
        val result = service.search(
            MarketSearchInput(
                productTitle = "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
                brandName = "Sony",
                modelName = "WH-1000XM5",
                sku = "SONY-WH1000XM5-BLK",
                currency = "USD",
                additionalAttributes = mapOf("monitoringMode" to "lookup_failure")
            )
        )

        assertEquals(MarketSearchStage.SEARCH_DISCOVERY, result.failure?.stage)
        assertEquals(true, result.failure?.retriable)
    }
}
