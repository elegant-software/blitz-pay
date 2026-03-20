package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.api.InvoiceAnalysisService
import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceExplanation
import com.elegant.software.blitzpay.invoice.api.InvoiceTotals
import com.elegant.software.blitzpay.invoice.api.InvoiceValidationResult
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class InvoiceAnalysisServiceImpl : InvoiceAnalysisService {

    override fun validate(invoiceData: InvoiceData): InvoiceValidationResult {
        val errors = mutableListOf<String>()

        if (invoiceData.invoiceNumber.isBlank()) {
            errors += "invoiceNumber must not be blank"
        }
        if (invoiceData.dueDate.isBefore(invoiceData.issueDate)) {
            errors += "dueDate must be on or after issueDate"
        }
        if (invoiceData.lineItems.isEmpty()) {
            errors += "at least one line item is required"
        }

        invoiceData.lineItems.forEachIndexed { index, item ->
            if (item.description.isBlank()) {
                errors += "lineItems[$index].description must not be blank"
            }
            if (item.quantity <= BigDecimal.ZERO) {
                errors += "lineItems[$index].quantity must be greater than zero"
            }
            if (item.unitPrice < BigDecimal.ZERO) {
                errors += "lineItems[$index].unitPrice must not be negative"
            }
            if (item.vatPercent < BigDecimal.ZERO) {
                errors += "lineItems[$index].vatPercent must not be negative"
            }
        }

        return InvoiceValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    override fun calculateTotals(invoiceData: InvoiceData): InvoiceTotals {
        val subtotal = invoiceData.lineItems
            .fold(BigDecimal.ZERO) { acc, item ->
                acc.add(item.quantity.multiply(item.unitPrice))
            }
            .setScale(2, RoundingMode.HALF_UP)

        val vatTotal = invoiceData.lineItems
            .fold(BigDecimal.ZERO) { acc, item ->
                val lineTotal = item.quantity.multiply(item.unitPrice)
                acc.add(lineTotal.multiply(item.vatPercent).divide(BigDecimal(100), 2, RoundingMode.HALF_UP))
            }
            .setScale(2, RoundingMode.HALF_UP)

        val grandTotal = subtotal.add(vatTotal).setScale(2, RoundingMode.HALF_UP)

        return InvoiceTotals(
            subtotal = subtotal,
            vatTotal = vatTotal,
            grandTotal = grandTotal,
            currency = invoiceData.currency
        )
    }

    override fun explain(invoiceData: InvoiceData): InvoiceExplanation {
        return InvoiceExplanation(
            invoiceNumber = invoiceData.invoiceNumber,
            sellerName = invoiceData.seller.name,
            buyerName = invoiceData.buyer.name,
            lineItemCount = invoiceData.lineItems.size,
            totals = calculateTotals(invoiceData)
        )
    }
}
