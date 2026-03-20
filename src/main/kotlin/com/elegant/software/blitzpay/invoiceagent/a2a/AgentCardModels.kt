package com.elegant.software.blitzpay.invoiceagent.a2a

data class AgentCard(
    val id: String,
    val name: String,
    val url: String,
    val description: String,
    val version: String,
    val protocolVersion: String = "0.3.0",
    val preferredTransport: String = "JSONRPC",
    val defaultInputModes: List<String> = listOf("text"),
    val defaultOutputModes: List<String> = listOf("text"),
    val skills: List<AgentSkill>
)

data class AgentSkill(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>
)
