package com.elegant.software.blitzpay.betterprice.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "market-search")
data class MarketSearchAgentProperties(
    val model: String = "gpt-4.1-mini",
    val baseUrl: String = "http://localhost:8080",
    val koog: KoogProperties = KoogProperties(),
    val a2a: A2aProperties = A2aProperties(),
    val card: AgentCardProperties = AgentCardProperties()
)

data class KoogProperties(
    val enabled: Boolean = true,
    val deterministicFallback: Boolean = true,
    val openAiApiKey: String = "",
    val systemPromptVersion: String = "product-price-research-v1"
)

data class A2aProperties(
    val port: Int = 8099,
    val path: String = "/a2a/market-search"
)

data class AgentCardProperties(
    val id: String = "market-search-agent",
    val name: String = "Market Search Agent",
    val description: String = "A2A KOOG market-search agent that uses configurable live providers and returns structured price-comparison and monitoring results",
    val version: String = "1.0.0"
)
