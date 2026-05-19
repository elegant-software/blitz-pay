package com.elegant.software.blitzpay.betterprice.agent.koog

import com.elegant.software.blitzpay.betterprice.agent.api.PriceComparisonChatRequest
import com.elegant.software.blitzpay.betterprice.agent.api.toDomain
import com.elegant.software.blitzpay.betterprice.agent.config.MarketSearchAgentProperties
import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ProductResearchKoogAgentFactory(
    private val toolRegistryFactory: ProductResearchToolRegistryFactory,
    private val monitoringMapper: KoogMonitoringMapper,
    private val systemPrompt: ProductResearchSystemPrompt,
    private val marketSearchProperties: MarketSearchProperties,
    private val marketSearchAgentProperties: MarketSearchAgentProperties
) {

    fun build(): ProductResearchKoogAgent = ProductResearchKoogAgent(
        toolRegistry = toolRegistryFactory.create(),
        monitoringMapper = monitoringMapper,
        session = ProductResearchKoogSession(
            sessionId = UUID.randomUUID().toString(),
            providerName = marketSearchProperties.provider,
            systemPromptVersion = marketSearchAgentProperties.koog.systemPromptVersion
        ),
        systemPrompt = systemPrompt.value()
    )
}

class ProductResearchKoogAgent(
    private val toolRegistry: ProductResearchToolRegistry,
    private val monitoringMapper: KoogMonitoringMapper,
    private val session: ProductResearchKoogSession,
    private val systemPrompt: String
) {

    fun run(request: PriceComparisonChatRequest): ProductResearchKoogRun {
        require(systemPrompt.isNotBlank()) { "KOOG system prompt must not be blank" }

        val searchPreview = runCatching {
            toolRegistry.marketSearchToolAdapter.search(request)
        }.getOrNull()

        val result = runCatching {
            toolRegistry.priceComparisonToolAdapter.compare(request)
        }.getOrElse { error ->
            monitoringMapper.agentFailure(
                request = request.toDomain(),
                message = error.message ?: "KOOG agent execution failed before price comparison completed"
            )
        }

        return ProductResearchKoogRun(
            session = session,
            searchPreview = searchPreview,
            result = monitoringMapper.merge(result, searchPreview)
        )
    }
}
