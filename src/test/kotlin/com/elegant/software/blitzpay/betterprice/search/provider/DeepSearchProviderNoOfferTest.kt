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
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

class DeepSearchProviderNoOfferTest {

    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()

    @Test
    fun `deepsearch provider returns no extracted offers when discovery is empty`() {
        val properties = MarketSearchProperties(
            enabled = true,
            provider = "deepsearch",
            requestTimeout = Duration.ofSeconds(2),
            deepsearch = DeepSearchProperties(
                apiKey = "test-key",
                baseUrl = "https://api.browserbase.com"
            ),
            brave = BraveSearchProperties()
        )
        val webClient = WebClient.builder()
            .exchangeFunction(ExchangeFunction {
                Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(TestFixtureLoader.deepSearchDiscoveryEmptyFixture())
                        .build()
                )
            })
            .build()

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
                productTitle = "Unlisted Product",
                currency = "USD"
            )
        )

        assertEquals(0, result.offers.size)
        assertEquals("partial_results", result.warnings.first().code)
    }
}
