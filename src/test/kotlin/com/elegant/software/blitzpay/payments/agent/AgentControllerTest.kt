package com.elegant.software.blitzpay.payments.agent

import com.elegant.software.blitzpay.agent.AgentController

import com.elegant.software.blitzpay.agent.api.AgentInfo
import com.embabel.agent.core.Action
import com.embabel.agent.core.Agent
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.Goal
import com.embabel.common.core.types.Semver
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(AgentController::class)
class AgentControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var agentPlatform: AgentPlatform

    @Test
    fun `GET agents returns list of registered agents`() {
        val mockAgent = Agent(
            name = "TestAgent",
            description = "A test agent",
            provider = "BlitzPay",
            version = Semver(0, 1, 0),
            actions = emptyList(),
            goals = emptySet(),
            conditions = emptySet()
        )
        
        whenever(agentPlatform.agents()).thenReturn(listOf(mockAgent))

        webTestClient.get()
            .uri("/v1/agents")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$").isArray
            .jsonPath("$[0].name").isEqualTo("TestAgent")
            .jsonPath("$[0].description").isEqualTo("A test agent")
            .jsonPath("$[0].provider").isEqualTo("BlitzPay")
    }

    @Test
    fun `GET agents by name returns agent details`() {
        val mockAgent = Agent(
            name = "InvoiceAgent",
            description = "Extract and process invoice information",
            provider = "BlitzPay",
            version = Semver(0, 1, 0),
            actions = emptyList(),
            goals = emptySet(),
            conditions = emptySet()
        )
        
        whenever(agentPlatform.agents()).thenReturn(listOf(mockAgent))

        webTestClient.get()
            .uri("/v1/agents/InvoiceAgent")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.name").isEqualTo("InvoiceAgent")
            .jsonPath("$.description").isEqualTo("Extract and process invoice information")
    }

    @Test
    fun `GET agents by name returns 404 for unknown agent`() {
        whenever(agentPlatform.agents()).thenReturn(emptyList())

        webTestClient.get()
            .uri("/v1/agents/UnknownAgent")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `GET agent process returns 404 for unknown process`() {
        whenever(agentPlatform.getAgentProcess("unknown-id")).thenReturn(null)

        webTestClient.get()
            .uri("/v1/agents/processes/unknown-id")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound
    }
}
