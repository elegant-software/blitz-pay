package com.elegant.software.blitzpay.betterprice.search.provider

import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class BrowserbaseFetchClient(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper,
    private val properties: MarketSearchProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun fetch(url: String): BrowserbaseFetchResult {
        require(properties.deepsearch.apiKey.isNotBlank()) { "DeepSearch API key is not configured" }

        val responseBody = webClient.post()
            .uri { builder ->
                builder.scheme("https")
                    .host(properties.deepsearch.baseUrl.removePrefix("https://").removePrefix("http://"))
                    .path(properties.deepsearch.fetchPath)
                    .build()
            }
            .header("X-BB-API-Key", properties.deepsearch.apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(
                mapOf(
                    "url" to url,
                    "allowRedirects" to properties.deepsearch.allowRedirects,
                    "allowInsecureSsl" to properties.deepsearch.allowInsecureSsl,
                    "proxies" to properties.deepsearch.proxies
                )
            )
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
            ?: return BrowserbaseFetchResult(statusCode = 502, content = "", contentType = null, encoding = null)

        log.info("Browserbase Fetch raw response for url='{}': {}", url, responseBody.compactForLog())

        val payload = objectMapper.readTree(responseBody)
        val result = BrowserbaseFetchResult(
            statusCode = payload.path("statusCode").asInt(200),
            content = payload.path("content").asText(""),
            contentType = payload.path("contentType").asText(null),
            encoding = payload.path("encoding").asText(null)
        )

        log.info(
            "Browserbase Fetch normalized response for url='{}': statusCode={}, contentType='{}', encoding='{}', contentLength={}",
            url,
            result.statusCode,
            result.contentType,
            result.encoding,
            result.content.length
        )

        return result
    }
}

data class BrowserbaseFetchResult(
    val statusCode: Int,
    val content: String,
    val contentType: String? = null,
    val encoding: String? = null
)

private fun String.compactForLog(maxLength: Int = 1000): String =
    replace(Regex("\\s+"), " ").trim().let { compact ->
        if (compact.length <= maxLength) compact else compact.take(maxLength) + "...[truncated]"
    }
