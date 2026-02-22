package com.elegant.software.blitzpay.invoice.api

import org.springframework.modulith.NamedInterface
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Public API for the invoice module, exposed to other Spring Modulith modules.
 *
 * Provides EU-standard (ZUGFeRD / Factur-X) invoice generation in both
 * machine-readable XML and human-readable PDF formats.
 */
@NamedInterface("InvoiceGateway")

interface InvoiceService {

    /**
     * Generates a ZUGFeRD 2.0 / Factur-X compliant XML representation of the invoice.
     *
     * @param invoiceData the invoice details
     * @return the XML content as a byte array (UTF-8 encoded)
     */
    fun generateXml(invoiceData: InvoiceData): ByteArray

    /**
     * Generates a human-readable PDF/A-3 document with the ZUGFeRD XML embedded,
     * compliant with EU e-invoicing standards.
     *
     * @param invoiceData the invoice details
     * @return the PDF content as a byte array
     */
    fun generatePdf(invoiceData: InvoiceData): ByteArray
}

data class InvoiceData(
    val invoiceNumber: String,
    val issueDate: LocalDate,
    val dueDate: LocalDate,
    val seller: TradePartyData,
    val buyer: TradePartyData,
    val lineItems: List<InvoiceLineItem>,
    val currency: String = "EUR",
    val bankAccount: BankAccountData? = null,
    val footerText: String? = null,
    val logoBase64: String? = null
)

data class TradePartyData(
    val name: String,
    val street: String,
    val zip: String,
    val city: String,
    val country: String,
    val vatId: String? = null
)

data class InvoiceLineItem(
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val vatPercent: BigDecimal
)

data class BankAccountData(
    val bankName: String,
    val iban: String,
    val bic: String
)
