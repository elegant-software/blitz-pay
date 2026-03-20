package com.elegant.software.blitzpay.invoiceagent.a2a

import com.elegant.software.blitzpay.invoiceagent.config.InvoiceAgentProperties
import org.springframework.stereotype.Component

@Component
class InvoiceAgentCardFactory(
    private val properties: InvoiceAgentProperties
) {

    fun create(): AgentCard {
        val agentPath = properties.a2a.path.removePrefix("/")
        return AgentCard(
            id = properties.card.id,
            name = properties.card.name,
            url = "${properties.baseUrl}/$agentPath",
            description = properties.card.description,
            version = properties.card.version,
            skills = listOf(
                AgentSkill(
                    id = "invoice-workflows",
                    name = "Invoice workflows",
                    description = "Create drafts, validate invoices, calculate totals, explain and render invoice documents",
                    tags = listOf("invoice", "zugferd", "factur-x", "validation", "calculation")
                )
            )
        )
    }
}
