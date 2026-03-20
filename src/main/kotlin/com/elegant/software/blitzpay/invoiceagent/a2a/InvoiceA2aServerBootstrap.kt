package com.elegant.software.blitzpay.invoiceagent.a2a

import com.elegant.software.blitzpay.invoiceagent.config.InvoiceAgentProperties
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "invoice-agent", name = ["enabled"], havingValue = "true")
class InvoiceA2aServerBootstrap(
    private val properties: InvoiceAgentProperties,
    private val cardFactory: InvoiceAgentCardFactory
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun logStartup() {
        val card = cardFactory.create()
        log.info(
            "Invoice A2A endpoint configured at {}{} (port hint: {}), agentCardId={}",
            properties.baseUrl,
            properties.a2a.path,
            properties.a2a.port,
            card.id
        )
    }
}
