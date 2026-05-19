package com.elegant.software.blitzpay.betterprice.agent.koog

import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchResult
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchWarning
import com.elegant.software.blitzpay.betterprice.agent.tool.MarketSearchToolAdapter
import com.elegant.software.blitzpay.betterprice.agent.tool.PriceComparisonToolAdapter
import com.elegant.software.blitzpay.betterprice.agent.tool.TestPriceComparisonFixtures
import com.elegant.software.blitzpay.support.TestFixtureLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProductResearchKoogFlowTest {

    private val marketSearchToolAdapter = mock<MarketSearchToolAdapter>()
    private val priceComparisonToolAdapter = mock<PriceComparisonToolAdapter>()
    private val agent = ProductResearchKoogAgent(
        toolRegistry = ProductResearchToolRegistry(
            marketSearchToolAdapter = marketSearchToolAdapter,
            priceComparisonToolAdapter = priceComparisonToolAdapter
        ),
        monitoringMapper = KoogMonitoringMapper(),
        session = ProductResearchKoogSession(
            sessionId = "session-1",
            providerName = "fixture",
            systemPromptVersion = "product-price-research-v1"
        ),
        systemPrompt = ProductResearchSystemPrompt().value()
    )

    @Test
    fun `run invokes search then comparison and merges warnings`() {
        val request = TestFixtureLoader.betterPriceScenario().inputData
        whenever(marketSearchToolAdapter.search(eq(request))).thenReturn(
            MarketSearchResult(
                offers = emptyList(),
                warnings = listOf(MarketSearchWarning(code = "partial_results", detail = "one hit had no offer"))
            )
        )
        whenever(priceComparisonToolAdapter.compare(eq(request))).thenReturn(
            TestPriceComparisonFixtures.betterPriceResult()
        )

        val run = agent.run(request)

        assertEquals("better_price_found", run.result.status.wireValue())
        assertEquals("completed", run.result.monitoring.stage.wireValue())
        assertTrue(run.result.monitoring.warnings.any { it.detail == "one hit had no offer" })
    }
}
