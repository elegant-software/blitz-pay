package com.elegant.software.blitzpay.invoiceagent.api

import com.elegant.software.blitzpay.invoiceagent.application.AgentResponse
import com.elegant.software.blitzpay.invoiceagent.application.InvoiceAgentService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty(prefix = "invoice-agent", name = ["enabled"], havingValue = "true")
@RequestMapping("/v1/invoice-agent")
class InvoiceAgentTestController(
    private val invoiceAgentService: InvoiceAgentService
) {

    @PostMapping("/chat")
    fun chat(@RequestBody request: InvoiceAgentChatRequest): AgentResponse {
        return invoiceAgentService.handleTextRequest(request.message)
    }
}

data class InvoiceAgentChatRequest(
    val message: String
)
