package com.elegant.software.blitzpay.invoiceagent.application

import com.elegant.software.blitzpay.invoiceagent.tool.InvoiceToolAdapter
import com.elegant.software.blitzpay.invoiceagent.tool.ToolResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

@Service
class InvoiceAgentService(
    private val invoiceToolAdapter: InvoiceToolAdapter,
    private val objectMapper: ObjectMapper
) {

    fun handleTextRequest(request: String): AgentResponse {
        val operation = detectOperation(request)
        val payload = extractJsonPayload(request)
            ?: return AgentResponse("Please provide an InvoiceData JSON payload.", false)

        val toolResult = when (operation) {
            InvoiceOperation.CREATE_DRAFT -> invoiceToolAdapter.createInvoiceDraft(payload)
            InvoiceOperation.VALIDATE -> invoiceToolAdapter.validateInvoice(payload)
            InvoiceOperation.CALCULATE_TOTALS -> invoiceToolAdapter.calculateInvoiceTotals(payload)
            InvoiceOperation.EXPLAIN -> invoiceToolAdapter.explainInvoice(payload)
            InvoiceOperation.GENERATE_XML -> invoiceToolAdapter.generateInvoiceXml(payload)
            InvoiceOperation.GENERATE_PDF -> invoiceToolAdapter.generateInvoicePdf(payload)
            InvoiceOperation.UNKNOWN -> ToolResult.failure(
                "Unsupported request. Supported intents: create draft, validate, calculate totals, explain, generate xml, generate pdf."
            )
        }

        return if (toolResult.success) {
            AgentResponse(toolResult.content, true)
        } else {
            AgentResponse(toolResult.error ?: "Invoice tool call failed", false)
        }
    }

    private fun detectOperation(text: String): InvoiceOperation {
        val normalized = text.lowercase()
        return when {
            "create" in normalized && "draft" in normalized -> InvoiceOperation.CREATE_DRAFT
            "validate" in normalized -> InvoiceOperation.VALIDATE
            "calculate" in normalized || "totals" in normalized -> InvoiceOperation.CALCULATE_TOTALS
            "explain" in normalized || "status" in normalized -> InvoiceOperation.EXPLAIN
            "xml" in normalized -> InvoiceOperation.GENERATE_XML
            "pdf" in normalized -> InvoiceOperation.GENERATE_PDF
            else -> InvoiceOperation.UNKNOWN
        }
    }

    private fun extractJsonPayload(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end <= start) {
            return null
        }

        val json = text.substring(start, end + 1)
        return runCatching {
            objectMapper.readTree(json)
            json
        }.getOrNull()
    }
}

data class AgentResponse(
    val message: String,
    val success: Boolean
)

enum class InvoiceOperation {
    CREATE_DRAFT,
    VALIDATE,
    CALCULATE_TOTALS,
    EXPLAIN,
    GENERATE_XML,
    GENERATE_PDF,
    UNKNOWN
}
