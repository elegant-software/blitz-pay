package com.elegant.software.blitzpay.invoiceagent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "invoice-agent")
data class InvoiceAgentProperties(
    val enabled: Boolean = false,
    val model: String = "gpt-4.1-mini",
    val baseUrl: String = "http://localhost:8080",
    val a2a: A2aProperties = A2aProperties(),
    val card: AgentCardProperties = AgentCardProperties()
)

data class A2aProperties(
    val port: Int = 8099,
    val path: String = "/a2a/invoice"
)

data class AgentCardProperties(
    val id: String = "invoice-agent",
    val name: String = "Invoice Agent",
    val description: String = "A2A invoice assistant powered by Koog and backed by BlitzPay invoice services",
    val version: String = "1.0.0"
)
