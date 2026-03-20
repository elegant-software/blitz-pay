package com.elegant.software.blitzpay.invoiceagent.a2a

import com.elegant.software.blitzpay.invoiceagent.application.InvoiceAgentService
import com.elegant.software.blitzpay.invoiceagent.config.InvoiceAgentProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@ConditionalOnProperty(prefix = "invoice-agent", name = ["enabled"], havingValue = "true")
@RequestMapping("\${invoice-agent.a2a.path:/a2a/invoice}")
class InvoiceA2aController(
    private val invoiceAgentService: InvoiceAgentService,
    private val cardFactory: InvoiceAgentCardFactory,
    private val properties: InvoiceAgentProperties
) {

    @GetMapping("/agent-card")
    fun agentCard(): AgentCard = cardFactory.create()

    @PostMapping
    fun sendMessage(@RequestBody request: A2aMessageSendRequest): A2aResponse {
        val textInput = request.params.message.parts.firstOrNull { it.type == "text" }?.text
            ?: ""

        val response = invoiceAgentService.handleTextRequest(textInput)
        val taskId = request.params.message.taskId ?: UUID.randomUUID().toString()

        return A2aResponse(
            id = request.id,
            result = A2aResult(
                message = A2aMessage(
                    role = "agent",
                    messageId = UUID.randomUUID().toString(),
                    contextId = request.params.message.contextId,
                    taskId = taskId,
                    parts = listOf(A2aPart(type = "text", text = response.message))
                ),
                status = if (response.success) "completed" else "failed"
            )
        )
    }
}
