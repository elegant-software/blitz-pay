package com.elegant.software.blitzpay.betterprice.search.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "market-search")
data class MarketSearchProperties(
    val enabled: Boolean = false,
    val provider: String = "brave",
    val maxHits: Int = 5,
    val maxPagesToFetch: Int = 3,
    val requestTimeout: Duration = Duration.ofSeconds(3),
    val brave: BraveSearchProperties = BraveSearchProperties(),
    val deepsearch: DeepSearchProperties = DeepSearchProperties()
)

data class BraveSearchProperties(
    val apiKey: String = "",
    val baseUrl: String = "https://api.search.brave.com",
    val searchPath: String = "/res/v1/web/search"
)

data class DeepSearchProperties(
    val apiKey: String = "",
    val baseUrl: String = "https://api.browserbase.com",
    val searchPath: String = "/v1/search",
    val fetchPath: String = "/v1/fetch",
    val allowRedirects: Boolean = true,
    val allowInsecureSsl: Boolean = false,
    val proxies: Boolean = false
)
