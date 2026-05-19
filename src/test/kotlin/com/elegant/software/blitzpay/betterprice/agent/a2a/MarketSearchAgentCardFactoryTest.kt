package com.elegant.software.blitzpay.betterprice.agent.a2a

import com.elegant.software.blitzpay.betterprice.agent.config.A2aProperties
import com.elegant.software.blitzpay.betterprice.agent.config.MarketSearchAgentProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarketSearchAgentCardFactoryTest {

    @Test
    fun `create advertises actual configured a2a listener url`() {
        val factory = MarketSearchAgentCardFactory(
            MarketSearchAgentProperties(
                baseUrl = "http://localhost:8080",
                a2a = A2aProperties(port = 8099, path = "/a2a/market-search")
            )
        )

        val card = factory.create()

        assertEquals("http://localhost:8099/a2a/market-search", card.url)
        assertTrue(card.capabilities.streaming == true)
        assertEquals("market-search-agent", card.skills.single().id)
    }
}
