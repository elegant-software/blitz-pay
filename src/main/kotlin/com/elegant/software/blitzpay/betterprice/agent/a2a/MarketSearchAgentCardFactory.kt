package com.elegant.software.blitzpay.betterprice.agent.a2a

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.TransportProtocol
import com.elegant.software.blitzpay.betterprice.agent.config.MarketSearchAgentProperties
import org.springframework.stereotype.Component
import java.net.URI

@Component
class MarketSearchAgentCardFactory(
    private val properties: MarketSearchAgentProperties
) {

    fun create(): AgentCard {
        val endpointUrl = a2aBaseUri().resolve(properties.a2a.path.removePrefix("/")).toString()
        return AgentCard(
            name = properties.card.name,
            description = properties.card.description,
            url = endpointUrl,
            version = properties.card.version,
            preferredTransport = TransportProtocol.JSONRPC,
            capabilities = AgentCapabilities(
                streaming = true,
                stateTransitionHistory = true
            ),
            defaultInputModes = listOf("text/plain", "application/json"),
            defaultOutputModes = listOf("text/plain", "application/json"),
            skills = listOf(
                AgentSkill(
                    id = properties.card.id,
                    name = "Market search",
                    description = "Use a KOOG market-search agent to discover live offers, compare a provided product price, and return structured monitoring data",
                    tags = listOf("market-search", "price-comparison", "offers", "monitoring"),
                    examples = listOf(
                        """{"inputPrice":329.99,"currency":"USD","productTitle":"Sony WH-1000XM5","brandName":"Sony"}"""
                    ),
                    inputModes = listOf("text/plain", "application/json"),
                    outputModes = listOf("application/json", "text/plain")
                )
            )
        )
    }

    private fun a2aBaseUri(): URI {
        val baseUri = URI.create(properties.baseUrl)
        val port = properties.a2a.port
        return URI(
            baseUri.scheme ?: "http",
            baseUri.userInfo,
            baseUri.host ?: "localhost",
            port,
            "/",
            null,
            null
        )
    }
}
