package com.elegant.software.blitzpay.betterprice.search.provider

import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchQuery
import com.elegant.software.blitzpay.betterprice.search.domain.SearchHit
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class DeepSearchClient(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper,
    private val properties: MarketSearchProperties
) : MarketDiscoveryProvider {

    private val log = LoggerFactory.getLogger(javaClass)
    override val providerName: String = "deepsearch"

    override fun search(query: MarketSearchQuery): List<SearchHit> {
        require(properties.deepsearch.apiKey.isNotBlank()) { "DeepSearch API key is not configured" }

        val responseBody = webClient.post()
            .uri { builder ->
                builder.scheme("https")
                    .host(properties.deepsearch.baseUrl.removePrefix("https://").removePrefix("http://"))
                    .path(properties.deepsearch.searchPath)
                    .build()
            }
            .header("x-bb-api-key", properties.deepsearch.apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(
                mapOf(
                    "query" to query.queryText,
                    "numResults" to query.maxHits
                )
            )
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
            ?: return emptyList()

        log.info(
            "Browserbase Deep Search raw response for query='{}': {}",
            query.queryText,
            responseBody.compactForLog()
        )

        val root = objectMapper.readTree(responseBody)
        val hits = candidateNodes(root)
            .mapIndexedNotNull { index, node -> node.toSearchHit(index + 1) }
            .filter { it.url.isNotBlank() }

        log.info(
            "Browserbase Deep Search normalized {} hits for query='{}'. First urls={}",
            hits.size,
            query.queryText,
            hits.take(3).map { it.url }
        )

        return hits
    }

    private fun candidateNodes(root: JsonNode): List<JsonNode> = sequenceOf(
        root.path("results"),
        root.path("items"),
        root.path("sources"),
        root.path("data").path("results"),
        root.path("web").path("results")
    )
        .firstOrNull { it.isArray && it.size() > 0 }
        ?.map { it }
        ?: emptyList()

    private fun JsonNode.toSearchHit(rank: Int): SearchHit? {
        val url = firstText("url", "link", "href") ?: return null
        return SearchHit(
            title = firstText("title", "name", "headline").orEmpty(),
            url = url,
            displayUrl = firstText("displayUrl", "display_url", "domain"),
            snippet = firstText("snippet", "description", "content"),
            rank = rank,
            providerName = providerName
        )
    }

    private fun JsonNode.firstText(vararg fieldNames: String): String? = fieldNames
        .firstNotNullOfOrNull { field ->
            path(field).takeIf { !it.isMissingNode && !it.isNull }?.asText(null)
        }
}

private fun String.compactForLog(maxLength: Int = 1000): String =
    replace(Regex("\\s+"), " ").trim().let { compact ->
        if (compact.length <= maxLength) compact else compact.take(maxLength) + "...[truncated]"
    }
