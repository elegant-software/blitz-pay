package com.elegant.software.blitzpay.invoiceagent.tool

import com.elegant.software.blitzpay.invoice.api.InvoiceAnalysisService
import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component
import java.util.Base64

@Component
class InvoiceToolAdapter(
    private val invoiceService: InvoiceService,
    private val invoiceAnalysisService: InvoiceAnalysisService,
    private val objectMapper: ObjectMapper
) {

    fun normalizeInvoiceInput(payload: String): ToolResult {
        return runCatching {
            val invoiceData = objectMapper.readValue<InvoiceData>(payload)
            ToolResult.success("normalized_invoice", objectMapper.writeValueAsString(invoiceData))
        }.getOrElse {
            ToolResult.failure("Unable to parse invoice payload as JSON InvoiceData: ${it.message}")
        }
    }

    fun validateInvoice(payload: String): ToolResult {
        return parseInvoice(payload).map { invoiceData ->
            val validation = invoiceAnalysisService.validate(invoiceData)
            ToolResult.success("validation", objectMapper.writeValueAsString(validation))
        }
    }

    fun calculateInvoiceTotals(payload: String): ToolResult {
        return parseInvoice(payload).map { invoiceData ->
            val totals = invoiceAnalysisService.calculateTotals(invoiceData)
            ToolResult.success("totals", objectMapper.writeValueAsString(totals))
        }
    }

    fun explainInvoice(payload: String): ToolResult {
        return parseInvoice(payload).map { invoiceData ->
            val explanation = invoiceAnalysisService.explain(invoiceData)
            ToolResult.success("explanation", objectMapper.writeValueAsString(explanation))
        }
    }

    fun createInvoiceDraft(payload: String): ToolResult = normalizeInvoiceInput(payload)

    fun generateInvoiceXml(payload: String): ToolResult {
        return parseInvoice(payload).map { invoiceData ->
            val xml = invoiceService.generateXml(invoiceData)
            val encoded = Base64.getEncoder().encodeToString(xml)
            ToolResult.success("zugferd_xml_base64", encoded)
        }
    }

    fun generateInvoicePdf(payload: String): ToolResult {
        return parseInvoice(payload).map { invoiceData ->
            val pdf = invoiceService.generatePdf(invoiceData)
            val encoded = Base64.getEncoder().encodeToString(pdf)
            ToolResult.success("zugferd_pdf_base64", encoded)
        }
    }

    private fun parseInvoice(payload: String): Result<InvoiceData> {
        return runCatching { objectMapper.readValue<InvoiceData>(payload) }
            .mapFailure { IllegalArgumentException("Invalid invoice payload: ${it.message}", it) }
    }

    private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> {
        return fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(transform(it)) }
        )
    }

    private fun <T> Result<T>.map(transform: (T) -> ToolResult): ToolResult {
        return fold(
            onSuccess = transform,
            onFailure = { ToolResult.failure(it.message ?: "Unknown invoice tool error") }
        )
    }
}

data class ToolResult(
    val success: Boolean,
    val type: String,
    val content: String,
    val error: String? = null
) {
    companion object {
        fun success(type: String, content: String) = ToolResult(true, type, content)
        fun failure(error: String) = ToolResult(false, "error", "", error)
    }
}
