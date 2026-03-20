package com.elegant.software.blitzpay.invoice.api

import org.springframework.modulith.NamedInterface
import java.math.BigDecimal

@NamedInterface("InvoiceGateway")
interface InvoiceAnalysisService {
    fun validate(invoiceData: InvoiceData): InvoiceValidationResult
    fun calculateTotals(invoiceData: InvoiceData): InvoiceTotals
    fun explain(invoiceData: InvoiceData): InvoiceExplanation
}

data class InvoiceValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

data class InvoiceTotals(
    val subtotal: BigDecimal,
    val vatTotal: BigDecimal,
    val grandTotal: BigDecimal,
    val currency: String
)

data class InvoiceExplanation(
    val invoiceNumber: String,
    val sellerName: String,
    val buyerName: String,
    val lineItemCount: Int,
    val totals: InvoiceTotals
)
