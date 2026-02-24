package com.elegant.software.blitzpay.agent

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.create
import com.embabel.agent.domain.io.UserInput
import com.embabel.agent.domain.library.HasContent
import com.embabel.common.ai.model.LlmOptions
import org.springframework.context.annotation.Profile
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Domain model for invoice line item extracted by the agent.
 */
data class InvoiceLineItemExtracted(
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val vatPercent: BigDecimal
)

/**
 * Domain model for trade party data extracted by the agent.
 */
data class TradePartyExtracted(
    val name: String,
    val street: String,
    val zip: String,
    val city: String,
    val country: String,
    val vatId: String?
)

/**
 * Domain model for invoice data extracted by the agent.
 */
data class InvoiceExtracted(
    val invoiceNumber: String,
    val issueDate: LocalDate,
    val dueDate: LocalDate,
    val seller: TradePartyExtracted,
    val buyer: TradePartyExtracted,
    val lineItems: List<InvoiceLineItemExtracted>,
    val currency: String
)

/**
 * Result of validating the invoice.
 */
data class InvoiceValidationResult(
    val isValid: Boolean,
    val issues: List<String>
)

/**
 * Final invoice generation result containing the extracted data and validation.
 */
data class InvoiceAgentResult(
    val invoice: InvoiceExtracted,
    val validation: InvoiceValidationResult,
    override val content: String
) : HasContent

/**
 * An Embabel Agent for processing and generating invoices.
 * This agent uses AI to extract invoice information from natural language input
 * and validates the extracted data.
 */
@Agent(description = "Extract and process invoice information from user input")
@Profile("!test")
class InvoiceAgent {

    /**
     * Extracts invoice data from natural language user input.
     * Uses an LLM to parse and structure the information.
     */
    @Action
    fun extractInvoiceData(userInput: UserInput, context: OperationContext): InvoiceExtracted =
        context.ai()
            .withLlm(LlmOptions.withAutoLlm().withTemperature(0.1))
            .create(
                """
                Extract invoice information from the following user input.
                Create a structured invoice with the following details:
                - Invoice number (generate if not provided, format: INV-YYYY-XXX)
                - Issue date (use today if not specified)
                - Due date (30 days from issue date if not specified)
                - Seller information (name, street, zip, city, country, VAT ID if available)
                - Buyer information (name, street, zip, city, country, VAT ID if available)
                - Line items (description, quantity, unit price, VAT percentage)
                - Currency (default to EUR if not specified)

                User input:
                ${userInput.content}
            """.trimIndent()
            )

    /**
     * Validates the extracted invoice data.
     * Checks for completeness and correctness.
     */
    @Action
    fun validateInvoice(invoice: InvoiceExtracted, context: OperationContext): InvoiceValidationResult =
        context.ai()
            .withLlm(LlmOptions.withAutoLlm().withTemperature(0.0))
            .create(
                """
                Validate the following invoice data and identify any issues:
                
                Invoice Number: ${invoice.invoiceNumber}
                Issue Date: ${invoice.issueDate}
                Due Date: ${invoice.dueDate}
                Currency: ${invoice.currency}
                
                Seller:
                - Name: ${invoice.seller.name}
                - Street: ${invoice.seller.street}
                - Zip: ${invoice.seller.zip}
                - City: ${invoice.seller.city}
                - Country: ${invoice.seller.country}
                - VAT ID: ${invoice.seller.vatId ?: "Not provided"}
                
                Buyer:
                - Name: ${invoice.buyer.name}
                - Street: ${invoice.buyer.street}
                - Zip: ${invoice.buyer.zip}
                - City: ${invoice.buyer.city}
                - Country: ${invoice.buyer.country}
                - VAT ID: ${invoice.buyer.vatId ?: "Not provided"}
                
                Line Items:
                ${invoice.lineItems.mapIndexed { i, item -> 
                    "${i+1}. ${item.description}: ${item.quantity} x ${item.unitPrice} (VAT: ${item.vatPercent}%)"
                }.joinToString("\n")}
                
                Check for:
                1. Required fields are present
                2. Dates are valid (issue date <= due date)
                3. Prices and quantities are positive
                4. VAT percentages are reasonable (0-100%)
                5. Country codes are valid
                
                Return validation result with isValid=true if all checks pass, 
                otherwise list the specific issues.
            """.trimIndent()
            )

    /**
     * Generates the final invoice result with all data and validation.
     * This action achieves the goal of the invoice processing workflow.
     */
    @AchievesGoal(description = "Invoice data has been extracted and validated from user input")
    @Action
    fun generateInvoiceResult(
        invoice: InvoiceExtracted,
        validation: InvoiceValidationResult,
        context: OperationContext
    ): InvoiceAgentResult {
        val summary = buildString {
            appendLine("# Invoice Summary")
            appendLine()
            appendLine("**Invoice Number:** ${invoice.invoiceNumber}")
            appendLine("**Issue Date:** ${invoice.issueDate}")
            appendLine("**Due Date:** ${invoice.dueDate}")
            appendLine("**Currency:** ${invoice.currency}")
            appendLine()
            appendLine("## Seller")
            appendLine("${invoice.seller.name}")
            appendLine("${invoice.seller.street}")
            appendLine("${invoice.seller.zip} ${invoice.seller.city}")
            appendLine("${invoice.seller.country}")
            invoice.seller.vatId?.let { appendLine("VAT: $it") }
            appendLine()
            appendLine("## Buyer")
            appendLine("${invoice.buyer.name}")
            appendLine("${invoice.buyer.street}")
            appendLine("${invoice.buyer.zip} ${invoice.buyer.city}")
            appendLine("${invoice.buyer.country}")
            invoice.buyer.vatId?.let { appendLine("VAT: $it") }
            appendLine()
            appendLine("## Line Items")
            invoice.lineItems.forEach { item ->
                val total = item.quantity.multiply(item.unitPrice)
                appendLine("- ${item.description}: ${item.quantity} x ${item.unitPrice} ${invoice.currency} = ${total} ${invoice.currency} (VAT: ${item.vatPercent}%)")
            }
            appendLine()
            appendLine("## Validation Status")
            if (validation.isValid) {
                appendLine("✓ Invoice data is valid")
            } else {
                appendLine("✗ Validation issues found:")
                validation.issues.forEach { issue ->
                    appendLine("  - $issue")
                }
            }
        }

        return InvoiceAgentResult(
            invoice = invoice,
            validation = validation,
            content = summary
        )
    }
}
