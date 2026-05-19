package com.elegant.software.blitzpay.betterprice.search.provider

import com.elegant.software.blitzpay.betterprice.search.application.MarketSearchQueryFactory
import com.elegant.software.blitzpay.betterprice.search.application.MarketSearchService
import com.elegant.software.blitzpay.betterprice.search.config.BraveSearchProperties
import com.elegant.software.blitzpay.betterprice.search.config.DeepSearchProperties
import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchInput
import com.elegant.software.blitzpay.betterprice.search.parser.RetailerStructuredDataParser
import com.elegant.software.blitzpay.support.TestFixtureLoader
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

class DeepSearchProviderTest {

    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val properties = MarketSearchProperties(
        enabled = true,
        provider = "deepsearch",
        maxHits = 5,
        maxPagesToFetch = 2,
        requestTimeout = Duration.ofSeconds(2),
        deepsearch = DeepSearchProperties(
            apiKey = "test-key",
            baseUrl = "https://api.browserbase.com"
        ),
        brave = BraveSearchProperties()
    )

    @Test
    fun `deepsearch client parses discovery response into ranked hits`() {
        val client = DeepSearchClient(
            webClient = webClient { request ->
                val body = when {
                    request.method().name() == "POST" &&
                        request.url().path.contains("/v1/search") &&
                        request.headers()["x-bb-api-key"]?.firstOrNull() == "test-key" ->
                        TestFixtureLoader.deepSearchDiscoverySonyFixture()
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
        assertEquals("deepsearch", hits.first().providerName)
        assertTrue(hits.all { it.providerName == "deepsearch" })
    }

    @Test
    fun `market search service extracts structured offers from deepsearch retailer pages`() {
        var fetchCallCount = 0
        val webClient = webClient { request ->
            val body = when {
                request.method().name() == "POST" && request.url().path.contains("/v1/search") ->
                    TestFixtureLoader.deepSearchDiscoverySonyFixture()
                request.method().name() == "POST" &&
                    request.url().path.contains("/v1/fetch") &&
                    ++fetchCallCount == 1 ->
                    browserbaseFetchJson(TestFixtureLoader.marketSearchFixtureText("retailer-sony-techhub.html"))
                request.method().name() == "POST" &&
                    request.url().path.contains("/v1/fetch") ->
                    browserbaseFetchJson(TestFixtureLoader.marketSearchFixtureText("retailer-empty.html"))
                else -> error("Unexpected request: ${request.url()}")
            }
            jsonOrHtmlResponse(request.url().toString(), body)
        }

        val service = MarketSearchService(
            properties = properties,
            queryFactory = MarketSearchQueryFactory(properties),
            discoveryProviders = listOf(DeepSearchClient(webClient, objectMapper, properties)),
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

    @Test
    fun `market search service follows outbound retailer link when info page has no price`() {
        var fetchCallCount = 0
        val webClient = webClient { request ->
            val body = when {
                request.method().name() == "POST" && request.url().path.contains("/v1/search") ->
                    objectMapper.writeValueAsString(
                        mapOf(
                            "results" to listOf(
                                mapOf(
                                    "title" to "Frontline Plus for Dogs",
                                    "url" to "https://frontline.example.test/plus-for-dogs",
                                    "snippet" to "Official product page"
                                )
                            )
                        )
                    )
                request.method().name() == "POST" &&
                    request.url().path.contains("/v1/fetch") &&
                    ++fetchCallCount == 1 ->
                    browserbaseFetchJson(
                        """
                        <html>
                          <head><title>FRONTLINE Plus for Dogs</title></head>
                          <body>
                            <h1>FRONTLINE Plus for Dogs</h1>
                            <a href="https://shop.example.test/frontline-plus">Buy now</a>
                          </body>
                        </html>
                        """.trimIndent()
                    )
                request.method().name() == "POST" &&
                    request.url().path.contains("/v1/fetch") ->
                    browserbaseFetchJson(TestFixtureLoader.marketSearchFixtureText("retailer-sony-techhub.html"))
                else -> error("Unexpected request: ${request.url()}")
            }
            jsonOrHtmlResponse(request.url().toString(), body)
        }

        val service = MarketSearchService(
            properties = properties,
            queryFactory = MarketSearchQueryFactory(properties),
            discoveryProviders = listOf(DeepSearchClient(webClient, objectMapper, properties)),
            browserbaseFetchClient = BrowserbaseFetchClient(webClient, objectMapper, properties),
            parser = RetailerStructuredDataParser(objectMapper),
            webClient = webClient
        )

        val result = service.search(
            MarketSearchInput(
                productTitle = "Frontline Plus for Dogs",
                brandName = "FRONTLINE",
                currency = "USD"
            )
        )

        assertEquals(1, result.offers.size)
        assertEquals("TechHub", result.offers.first().sellerName)
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

    private fun browserbaseFetchJson(content: String): String = objectMapper.writeValueAsString(
        mapOf(
            "statusCode" to 200,
            "contentType" to "text/html; charset=utf-8",
            "encoding" to "utf-8",
            "content" to content
        )
    )
}
