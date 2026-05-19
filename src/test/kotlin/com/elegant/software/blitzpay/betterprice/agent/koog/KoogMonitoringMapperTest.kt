package com.elegant.software.blitzpay.betterprice.agent.koog

import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchBottleneck
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchResult
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchStage
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchWarning
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ProductLookupRequest
import com.elegant.software.blitzpay.betterprice.agent.tool.TestPriceComparisonFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class KoogMonitoringMapperTest {

    private val mapper = KoogMonitoringMapper()

    @Test
    fun `merge adds provider warnings and bottleneck to final result`() {
        val merged = mapper.merge(
            result = TestPriceComparisonFixtures.betterPriceResult(),
            searchPreview = MarketSearchResult(
                offers = emptyList(),
                warnings = listOf(MarketSearchWarning(code = "partial_results", detail = "missing structured offer")),
                bottleneck = MarketSearchBottleneck(
                    stage = MarketSearchStage.SEARCH_DISCOVERY,
                    reason = "provider_timeout",
                    detail = "search was slow"
                )
            )
        )

        assertTrue(merged.monitoring.warnings.any { it.detail == "missing structured offer" })
        assertEquals("provider_timeout", merged.monitoring.bottleneck?.reason)
    }

    @Test
    fun `agentFailure returns structured KOOG failure`() {
        val failure = mapper.agentFailure(
            request = ProductLookupRequest(
                inputPrice = BigDecimal("38.00"),
                currency = "EUR",
                productTitle = "Frontline Spot On Dog S Solution"
            ),
            message = "KOOG agent execution failed before price comparison completed"
        )

        assertEquals("comparison_unavailable", failure.status.wireValue())
        assertEquals("agent_execution_failed", failure.explanationCode.wireValue())
        assertEquals("agent_execution_failed", failure.monitoring.failure?.code?.wireValue())
    }
}
