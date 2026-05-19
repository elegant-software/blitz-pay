package com.elegant.software.blitzpay.betterprice.agent.application

import com.elegant.software.blitzpay.betterprice.agent.api.PriceComparisonChatRequest
import com.elegant.software.blitzpay.betterprice.agent.api.toDto
import com.elegant.software.blitzpay.betterprice.agent.koog.ProductResearchKoogAgentFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service

@Service
class MarketSearchAgentService(
    private val koogAgentFactory: ProductResearchKoogAgentFactory,
    private val objectMapper: ObjectMapper
) {
    private val jsonMapper = objectMapper.copy().findAndRegisterModules()

    fun handleTextRequest(request: String): AgentResponse {
        val payload = extractJsonPayload(request)
            ?: return AgentResponse("Please provide a product price comparison JSON payload.", false)

        return runCatching {
            val structuredRequest = jsonMapper.readValue<PriceComparisonChatRequest>(payload)
            val result = koogAgentFactory.build().run(structuredRequest).result.toDto()
            AgentResponse(jsonMapper.writeValueAsString(result), true)
        }.getOrElse {
            AgentResponse(it.message ?: "Price comparison tool call failed", false)
        }
    }

    private fun extractJsonPayload(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return runCatching {
                objectMapper.readTree(trimmed)
                trimmed
            }.getOrNull()
        }

        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end <= start) {
            return null
        }

        val json = text.substring(start, end + 1)
        return runCatching {
            objectMapper.readTree(json)
            json
        }.getOrNull()
    }
}

data class AgentResponse(
    val message: String,
    val success: Boolean
)
