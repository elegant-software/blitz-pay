package com.elegant.software.blitzpay.invoiceagent.koog

import com.elegant.software.blitzpay.invoiceagent.application.InvoiceAgentService
import org.springframework.stereotype.Component

@Component
class InvoiceKoogAgentFactory(
    private val invoiceAgentService: InvoiceAgentService,
    private val systemPrompt: InvoiceAgentSystemPrompt
) {

    fun build(): InvoiceKoogAgent {
        return InvoiceKoogAgent(
            systemPrompt = systemPrompt.value(),
            executor = invoiceAgentService::handleTextRequest
        )
    }
}

data class InvoiceKoogAgent(
    val systemPrompt: String,
    val executor: (String) -> Any
)
