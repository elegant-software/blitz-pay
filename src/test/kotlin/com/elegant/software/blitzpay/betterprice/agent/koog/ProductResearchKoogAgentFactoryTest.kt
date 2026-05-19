package com.elegant.software.blitzpay.betterprice.agent.koog

import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchResult
import com.elegant.software.blitzpay.betterprice.search.config.MarketSearchProperties
import com.elegant.software.blitzpay.betterprice.agent.config.KoogProperties
import com.elegant.software.blitzpay.betterprice.agent.config.MarketSearchAgentProperties
import com.elegant.software.blitzpay.betterprice.agent.tool.MarketSearchToolAdapter
import com.elegant.software.blitzpay.betterprice.agent.tool.PriceComparisonToolAdapter
import com.elegant.software.blitzpay.betterprice.agent.tool.TestPriceComparisonFixtures
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProductResearchKoogAgentFactoryTest {

    @Test
    fun `build creates session with configured provider and prompt version`() {
        val marketSearchToolAdapter = mock<MarketSearchToolAdapter>()
        val priceComparisonToolAdapter = mock<PriceComparisonToolAdapter>()
        val factory = ProductResearchKoogAgentFactory(
            toolRegistryFactory = ProductResearchToolRegistryFactory(
                marketSearchToolAdapter = marketSearchToolAdapter,
                priceComparisonToolAdapter = priceComparisonToolAdapter
            ),
            monitoringMapper = KoogMonitoringMapper(),
            systemPrompt = ProductResearchSystemPrompt(),
            marketSearchProperties = MarketSearchProperties(provider = "deepsearch"),
            marketSearchAgentProperties = MarketSearchAgentProperties(
                koog = KoogProperties(systemPromptVersion = "v-test")
            )
        )
        whenever(marketSearchToolAdapter.search(org.mockito.kotlin.any())).thenReturn(MarketSearchResult(offers = emptyList()))
        whenever(priceComparisonToolAdapter.compare(org.mockito.kotlin.any())).thenReturn(
            TestPriceComparisonFixtures.betterPriceResult()
        )

        val agent = assertDoesNotThrow<ProductResearchKoogAgent> { factory.build() }
        val run = agent.run(
            com.elegant.software.blitzpay.support.TestFixtureLoader.betterPriceScenario().inputData
        )

        assertEquals("deepsearch", run.session.providerName)
        assertEquals("v-test", run.session.systemPromptVersion)
    }
}
