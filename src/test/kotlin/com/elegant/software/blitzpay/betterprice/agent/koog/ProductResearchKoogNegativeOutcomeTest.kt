package com.elegant.software.blitzpay.betterprice.agent.koog

import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchResult
import com.elegant.software.blitzpay.betterprice.agent.tool.MarketSearchToolAdapter
import com.elegant.software.blitzpay.betterprice.agent.tool.PriceComparisonToolAdapter
import com.elegant.software.blitzpay.betterprice.agent.tool.TestPriceComparisonFixtures
import com.elegant.software.blitzpay.support.TestFixtureLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProductResearchKoogNegativeOutcomeTest {

    private val marketSearchToolAdapter = mock<MarketSearchToolAdapter>()
    private val priceComparisonToolAdapter = mock<PriceComparisonToolAdapter>()
    private val agent = ProductResearchKoogAgent(
        toolRegistry = ProductResearchToolRegistry(
            marketSearchToolAdapter = marketSearchToolAdapter,
            priceComparisonToolAdapter = priceComparisonToolAdapter
        ),
        monitoringMapper = KoogMonitoringMapper(),
        session = ProductResearchKoogSession(
            sessionId = "session-2",
            providerName = "deepsearch",
            systemPromptVersion = "product-price-research-v1"
        ),
        systemPrompt = ProductResearchSystemPrompt().value()
    )

    @Test
    fun `run preserves no better price outcomes`() {
        val request = TestFixtureLoader.noBetterPriceScenario().inputData
        whenever(marketSearchToolAdapter.search(eq(request))).thenReturn(MarketSearchResult(offers = emptyList()))
        whenever(priceComparisonToolAdapter.compare(eq(request))).thenReturn(
            TestPriceComparisonFixtures.noBetterPriceResult()
        )

        val run = agent.run(request)

        assertEquals("no_better_price_found", run.result.status.wireValue())
        assertEquals("no_cheaper_qualifying_offer", run.result.explanationCode.wireValue())
    }

    @Test
    fun `run returns agent execution failure when comparison tool throws`() {
        val request = TestFixtureLoader.betterPriceScenario().inputData
        whenever(marketSearchToolAdapter.search(eq(request))).thenReturn(MarketSearchResult(offers = emptyList()))
        whenever(priceComparisonToolAdapter.compare(eq(request))).thenThrow(
            IllegalStateException("comparison exploded")
        )

        val run = agent.run(request)

        assertEquals("comparison_unavailable", run.result.status.wireValue())
        assertEquals("agent_execution_failed", run.result.explanationCode.wireValue())
        assertEquals("agent_execution_failed", run.result.monitoring.failure?.code?.wireValue())
    }
}
