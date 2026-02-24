package com.elegant.software.blitzpay.agent.api

import org.springframework.modulith.NamedInterface

/**
 * Public API for the agent module, exposed to other Spring Modulith modules.
 *
 * Provides access to registered agents and their runtime information.
 */
@NamedInterface("AgentGateway")

/**
 * Summary information about a registered agent.
 */
data class AgentInfo(
    val name: String,
    val description: String,
    val provider: String,
    val version: String,
    val goals: List<String>,
    val actions: List<String>,
    val conditions: List<String>
)

/**
 * Information about a running or completed agent process.
 */
data class AgentProcessInfo(
    val id: String,
    val agentName: String,
    val status: String,
    val startTime: String?,
    val endTime: String?
)

/**
 * Request to run an agent for invoice generation.
 */
data class InvoiceAgentRequest(
    val userInput: String
)

/**
 * Response from an invoice agent execution.
 */
data class InvoiceAgentResponse(
    val processId: String,
    val status: String,
    val result: String?
)
