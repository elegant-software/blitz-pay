package com.elegant.software.blitzpay.betterprice.agent.application

import com.elegant.software.blitzpay.betterprice.agent.api.toDto
import com.elegant.software.blitzpay.betterprice.agent.koog.KoogMonitoringMapper
import com.elegant.software.blitzpay.betterprice.agent.tool.TestPriceComparisonFixtures
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ProductLookupRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PriceComparisonResultSerializationTest {

    private val objectMapper = ObjectMapper().registerKotlinModule().findAndRegisterModules()

    @Test
    fun `serializes result using structured wire values`() {
        val json = objectMapper.writeValueAsString(TestPriceComparisonFixtures.betterPriceResult().toDto())

        assertTrue(json.contains("\"status\":\"better_price_found\""))
        assertTrue(json.contains("\"matchConfidence\":\"high\""))
        assertTrue(json.contains("\"qualificationStatus\":\"qualified\""))
        assertTrue(json.contains("\"savingsAmount\":50.00"))
        assertTrue(json.contains("\"monitoring\":{\"stage\":\"completed\",\"progress\":100"))
    }

    @Test
    fun `serializes KOOG agent execution failures using machine readable codes`() {
        val json = objectMapper.writeValueAsString(
            KoogMonitoringMapper().agentFailure(
                request = ProductLookupRequest(
                    inputPrice = BigDecimal("38.00"),
                    currency = "EUR",
                    productTitle = "Frontline Spot On Dog S Solution"
                ),
                message = "KOOG agent execution failed before price comparison completed"
            ).toDto()
        )

        assertTrue(json.contains("\"status\":\"comparison_unavailable\""))
        assertTrue(json.contains("\"explanationCode\":\"agent_execution_failed\""))
        assertTrue(json.contains("\"failure\":{\"code\":\"agent_execution_failed\""))
    }
}
