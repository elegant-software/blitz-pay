package com.elegant.software.blitzpay.invoiceagent

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID
import org.mockito.kotlin.whenever

@WebFluxTest(InvoiceAgentController::class)
class InvoiceAgentControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var excelInvoiceAgentService: ExcelInvoiceAgentService

    @Test
    fun `POST starts agent run`() {
        val runId = UUID.randomUUID()
        whenever(excelInvoiceAgentService.start("/tmp/invoices.xlsx")).thenReturn(
            AgentRunStatus(
                runId = runId,
                filePath = "/tmp/invoices.xlsx",
                state = AgentRunState.PENDING,
                startedAt = Instant.now()
            )
        )

        webTestClient.post()
            .uri("/v1/agents/invoice")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"filePath":"/tmp/invoices.xlsx"}""")
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("$.runId").isEqualTo(runId.toString())
            .jsonPath("$.state").isEqualTo("PENDING")
    }
}
