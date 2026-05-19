package com.elegant.software.blitzpay.betterprice.search.provider

import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchQuery
import com.elegant.software.blitzpay.betterprice.search.domain.SearchHit
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class BraveSearchClient(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper,
    private val properties: MarketSearchProperties
) : MarketDiscoveryProvider {

    override val providerName: String = "brave"

    override fun search(query: MarketSearchQuery): List<SearchHit> {
        require(properties.brave.apiKey.isNotBlank()) { "Brave Search API key is not configured" }

        val responseBody = webClient.get()
            .uri { builder ->
                builder.scheme("https")
                    .host(properties.brave.baseUrl.removePrefix("https://").removePrefix("http://"))
                    .path(properties.brave.searchPath)
                    .queryParam("q", query.queryText)
                    .queryParam("count", query.maxHits)
                    .build()
            }
            .header("X-Subscription-Token", properties.brave.apiKey)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
            ?: return emptyList()

        val root = objectMapper.readTree(responseBody)
        return root.path("web").path("results")
            .mapIndexed { index, result -> result.toSearchHit(index + 1) }
            .filter { it.url.isNotBlank() }
    }

    private fun JsonNode.toSearchHit(rank: Int): SearchHit = SearchHit(
        title = path("title").asText(""),
        url = path("url").asText(""),
        displayUrl = path("meta_url").path("display_url").asText(null),
        snippet = path("description").asText(null),
        rank = rank,
        providerName = "brave"
    )
}
