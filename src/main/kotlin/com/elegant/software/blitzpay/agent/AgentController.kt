package com.elegant.software.blitzpay.agent

import com.elegant.software.blitzpay.agent.api.AgentInfo
import com.elegant.software.blitzpay.agent.api.AgentProcessInfo
import com.elegant.software.blitzpay.agent.api.InvoiceAgentRequest
import com.elegant.software.blitzpay.agent.api.InvoiceAgentResponse
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentProcess
import com.embabel.agent.core.ProcessOptions
import com.embabel.agent.domain.io.UserInput
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * REST Controller for exposing Embabel Agent information and functionality.
 * Provides endpoints to list agents, view agent details, and execute the invoice agent.
 */
@Tag(name = "Agents", description = "Embabel AI Agent management and execution")
@RestController
@RequestMapping("/v1/agents", version = "1")
class AgentController(
    private val agentPlatform: AgentPlatform
) {

    private val dateTimeFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault())

    /**
     * Lists all registered agents on the platform.
     */
    @Operation(
        summary = "List all agents",
        description = "Returns information about all registered agents on the Embabel platform"
    )
    @GetMapping
    fun listAgents(): ResponseEntity<List<AgentInfo>> {
        val agents = agentPlatform.agents().map { agent ->
            AgentInfo(
                name = agent.name,
                description = agent.description,
                provider = agent.provider,
                version = agent.version.toString(),
                goals = agent.goals.map { it.description },
                actions = agent.actions.map { it.name },
                conditions = agent.conditions.map { it.name }
            )
        }
        return ResponseEntity.ok(agents)
    }

    /**
     * Gets detailed information about a specific agent.
     */
    @Operation(
        summary = "Get agent details",
        description = "Returns detailed information about a specific agent by name"
    )
    @GetMapping("/{agentName}")
    fun getAgent(@PathVariable agentName: String): ResponseEntity<AgentInfo> {
        val agent = agentPlatform.agents().find { it.name == agentName }
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            AgentInfo(
                name = agent.name,
                description = agent.description,
                provider = agent.provider,
                version = agent.version.toString(),
                goals = agent.goals.map { it.description },
                actions = agent.actions.map { it.name },
                conditions = agent.conditions.map { it.name }
            )
        )
    }

    /**
     * Gets information about a specific agent process.
     */
    @Operation(
        summary = "Get agent process status",
        description = "Returns status information about a running or completed agent process"
    )
    @GetMapping("/processes/{processId}")
    fun getAgentProcess(@PathVariable processId: String): ResponseEntity<AgentProcessInfo> {
        val process = agentPlatform.getAgentProcess(processId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(mapProcessToInfo(process))
    }

    /**
     * Runs the invoice agent with the given user input.
     */
    @Operation(
        summary = "Run invoice agent",
        description = "Executes the invoice extraction agent with natural language input"
    )
    @PostMapping("/invoice/run")
    fun runInvoiceAgent(@RequestBody request: InvoiceAgentRequest): ResponseEntity<InvoiceAgentResponse> {
        val invoiceAgent = agentPlatform.agents().find { it.name == "InvoiceAgent" }
            ?: return ResponseEntity.badRequest().build()

        val userInput = UserInput(request.userInput)
        
        val process = agentPlatform.runAgentFrom(
            agent = invoiceAgent,
            processOptions = ProcessOptions(),
            bindings = mapOf("userInput" to userInput)
        )

        // Get any InvoiceAgentResult from the blackboard if available
        val result = process.blackboard.lastResult()?.let {
            if (it is InvoiceAgentResult) it.content else null
        }

        return ResponseEntity.ok(
            InvoiceAgentResponse(
                processId = process.id,
                status = process.status.name,
                result = result
            )
        )
    }

    private fun mapProcessToInfo(process: AgentProcess): AgentProcessInfo {
        return AgentProcessInfo(
            id = process.id,
            agentName = process.agent.name,
            status = process.status.name,
            startTime = dateTimeFormatter.format(process.timestamp),
            endTime = if (process.finished) {
                dateTimeFormatter.format(process.timestamp.plus(process.runningTime))
            } else {
                null
            }
        )
    }
}
