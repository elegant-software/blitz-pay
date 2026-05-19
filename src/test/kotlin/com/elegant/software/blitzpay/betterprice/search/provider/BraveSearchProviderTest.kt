package com.elegant.software.blitzpay.betterprice.search.provider

import com.elegant.software.blitzpay.betterprice.search.application.MarketSearchQueryFactory
import com.elegant.software.blitzpay.betterprice.search.application.MarketSearchService
import com.elegant.software.blitzpay.betterprice.search.config.BraveSearchProperties
import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchInput
import com.elegant.software.blitzpay.betterprice.search.parser.RetailerStructuredDataParser
import com.elegant.software.blitzpay.support.TestFixtureLoader
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

class BraveSearchProviderTest {

    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val properties = MarketSearchProperties(
        enabled = true,
        provider = "brave",
        maxHits = 5,
        maxPagesToFetch = 2,
        requestTimeout = Duration.ofSeconds(2),
        brave = BraveSearchProperties(
            apiKey = "test-key",
            baseUrl = "https://api.search.brave.com"
        )
    )

    @Test
    fun `brave client parses discovery response into ranked hits`() {
        val client = BraveSearchClient(
            webClient = webClient { request ->
                val body = when {
                    request.url().path.contains("/res/v1/web/search") ->
                        TestFixtureLoader.marketSearchFixtureText("brave-discovery-sony.json")
                    else -> error("Unexpected path: ${request.url()}")
                }
                jsonResponse(body)
            },
            objectMapper = objectMapper,
            properties = properties
        )

        val hits = client.search(
            MarketSearchQueryFactory(properties).create(
                MarketSearchInput(
                    productTitle = "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
                    brandName = "Sony",
                    modelName = "WH-1000XM5",
                    sku = "SONY-WH1000XM5-BLK",
                    currency = "USD"
                )
            )
        )

        assertEquals(2, hits.size)
        assertEquals("https://example.test/offers/sony-wh1000xm5-techhub", hits.first().url)
        assertEquals(1, hits.first().rank)
    }

    @Test
    fun `market search service extracts structured offers from fetched retailer pages`() {
        val webClient = webClient { request ->
            val body = when {
                request.url().path.contains("/res/v1/web/search") ->
                    TestFixtureLoader.marketSearchFixtureText("brave-discovery-sony.json")
                request.url().toString().contains("sony-wh1000xm5-techhub") ->
                    TestFixtureLoader.marketSearchFixtureText("retailer-sony-techhub.html")
                request.url().toString().contains("sony-wh1000xm5-audiodeals") ->
                    TestFixtureLoader.marketSearchFixtureText("retailer-empty.html")
                else -> error("Unexpected request: ${request.url()}")
            }
            jsonOrHtmlResponse(request.url().toString(), body)
        }

        val service = MarketSearchService(
            properties = properties,
            queryFactory = MarketSearchQueryFactory(properties),
            discoveryProviders = listOf(BraveSearchClient(webClient, objectMapper, properties)),
            browserbaseFetchClient = BrowserbaseFetchClient(webClient, objectMapper, properties),
            parser = RetailerStructuredDataParser(objectMapper),
            webClient = webClient
        )

        val result = service.search(
            MarketSearchInput(
                productTitle = "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
                brandName = "Sony",
                modelName = "WH-1000XM5",
                sku = "SONY-WH1000XM5-BLK",
                currency = "USD"
            )
        )

        assertEquals(1, result.offers.size)
        assertEquals("TechHub", result.offers.first().sellerName)
        assertEquals("no_extractable_offer", result.warnings.first().code)
    }

    private fun webClient(handler: (org.springframework.web.reactive.function.client.ClientRequest) -> ClientResponse): WebClient =
        WebClient.builder()
            .exchangeFunction(ExchangeFunction { request -> Mono.just(handler(request)) })
            .build()

    private fun jsonResponse(body: String): ClientResponse =
        ClientResponse.create(HttpStatus.OK)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .build()

    private fun jsonOrHtmlResponse(url: String, body: String): ClientResponse =
        ClientResponse.create(HttpStatus.OK)
            .header(
                "Content-Type",
                if (url.endsWith(".json") || body.trimStart().startsWith("{")) MediaType.APPLICATION_JSON_VALUE else MediaType.TEXT_HTML_VALUE
            )
            .body(body)
            .build()
}
