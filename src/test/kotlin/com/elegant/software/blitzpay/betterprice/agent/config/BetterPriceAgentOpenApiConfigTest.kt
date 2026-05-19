package com.elegant.software.blitzpay.betterprice.agent.config

import io.swagger.v3.oas.models.OpenAPI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BetterPriceAgentOpenApiConfigTest {

    @Test
    fun `documents A2A operations on dedicated listener port`() {
        val properties = MarketSearchAgentProperties(
            baseUrl = "http://localhost:8080",
            a2a = A2aProperties(port = 8099, path = "/a2a/market-search")
        )

        val groupedOpenApi = BetterPriceAgentOpenApiConfig(properties).betterPriceAgentApi()
        val openApi = OpenAPI()
        groupedOpenApi.openApiCustomizers.forEach { customizer -> customizer.customise(openApi) }

        val agentCardGet = assertNotNull(openApi.paths["/.well-known/agent-card.json"]?.get)
        val marketSearchPost = assertNotNull(openApi.paths["/a2a/market-search"]?.post)

        assertEquals("http://localhost:8099", agentCardGet.servers.single().url)
        assertEquals("http://localhost:8099", marketSearchPost.servers.single().url)
    }
}
