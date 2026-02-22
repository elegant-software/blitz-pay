package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceLineItem
import com.elegant.software.blitzpay.invoice.api.InvoiceService
import com.elegant.software.blitzpay.invoice.api.TradePartyData
import org.mustangproject.Invoice
import org.mustangproject.Item
import org.mustangproject.Product
import org.mustangproject.TradeParty
import org.mustangproject.ZUGFeRD.Profiles
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3
import org.xhtmlrenderer.pdf.ITextRenderer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Date
import java.time.ZoneId

/**
 * Implementation of [InvoiceService] backed by the Mustang Project library
 * and Thymeleaf templating engine for professional PDF rendering.
 *
 * Generates EU-standard ZUGFeRD 2.0 / Factur-X invoices in both XML and PDF formats.
 */
@Service
class ZugferdInvoiceService(
    private val templateEngine: TemplateEngine
) : InvoiceService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun generateXml(invoiceData: InvoiceData): ByteArray {
        val invoice = toMustangInvoice(invoiceData)
        val provider = ZUGFeRD2PullProvider()
        provider.setProfile(Profiles.getByName("EN16931"))
        provider.generateXML(invoice)
        return provider.xml
    }

    override fun generatePdf(invoiceData: InvoiceData): ByteArray {
        val invoice = toMustangInvoice(invoiceData)

        val basePdf = renderBasePdf(invoiceData)

        val exporter = ZUGFeRDExporterFromA3()
            .ignorePDFAErrors()
            .setProducer("BlitzPay")
            .setCreator("BlitzPay Invoice Module")
            .setZUGFeRDVersion(2)
            .setProfile(Profiles.getByName("EN16931"))
            .load(basePdf)

        exporter.setTransaction(invoice)

        val output = ByteArrayOutputStream()
        exporter.export(output)
        return output.toByteArray()
    }

    internal fun toMustangInvoice(data: InvoiceData): Invoice {
        val invoice = Invoice()
            .setNumber(data.invoiceNumber)
            .setIssueDate(toDate(data.issueDate))
            .setDueDate(toDate(data.dueDate))
            .setDeliveryDate(toDate(data.issueDate))
            .setCurrency(data.currency)
            .setSender(toTradeParty(data.seller))
            .setRecipient(toTradeParty(data.buyer))

        data.lineItems.forEach { item ->
            invoice.addItem(toItem(item))
        }

        return invoice
    }

    private fun toTradeParty(data: TradePartyData): TradeParty {
        val party = TradeParty(data.name, data.street, data.zip, data.city, data.country)
        data.vatId?.let { party.addVATID(it) }
        return party
    }

    private fun toItem(data: InvoiceLineItem): Item {
        val product = Product(data.description, "", "H87", data.vatPercent)
        return Item(product, data.unitPrice, data.quantity)
    }

    private fun toDate(localDate: LocalDate): Date {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    /**
     * Renders a professional human-readable PDF invoice using Thymeleaf and Flying Saucer.
     * Supports company logo, bank account details, and custom footer text.
     */
    internal fun renderBasePdf(data: InvoiceData): ByteArray {
        val html = renderHtml(data)

        val baos = ByteArrayOutputStream()
        val renderer = ITextRenderer()
        renderer.setDocumentFromString(html)
        renderer.layout()
        renderer.createPDF(baos)
        return baos.toByteArray()
    }

    /**
     * Renders the invoice HTML using the Thymeleaf template engine.
     */
    internal fun renderHtml(data: InvoiceData): String {
        data class LineItemView(
            val description: String,
            val quantity: BigDecimal,
            val unitPrice: BigDecimal,
            val vatPercent: BigDecimal,
            val lineTotal: BigDecimal
        )

        val lineItemViews = data.lineItems.map { item ->
            LineItemView(
                description = item.description,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                vatPercent = item.vatPercent,
                lineTotal = item.quantity.multiply(item.unitPrice).setScale(2, RoundingMode.HALF_UP)
            )
        }

        val subtotal = lineItemViews.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.lineTotal) }
            .setScale(2, RoundingMode.HALF_UP)
        val vatTotal = lineItemViews.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(item.lineTotal.multiply(item.vatPercent).divide(BigDecimal(100), 2, RoundingMode.HALF_UP))
        }.setScale(2, RoundingMode.HALF_UP)
        val grandTotal = subtotal.add(vatTotal).setScale(2, RoundingMode.HALF_UP)

        val context = Context()
        context.setVariable("invoiceNumber", data.invoiceNumber)
        context.setVariable("issueDate", data.issueDate.toString())
        context.setVariable("dueDate", data.dueDate.toString())
        context.setVariable("seller", data.seller)
        context.setVariable("buyer", data.buyer)
        context.setVariable("lineItems", lineItemViews)
        context.setVariable("currency", data.currency)
        context.setVariable("subtotal", subtotal)
        context.setVariable("vatTotal", vatTotal)
        context.setVariable("grandTotal", grandTotal)
        context.setVariable("bankAccount", data.bankAccount)
        context.setVariable("footerText", data.footerText)
        context.setVariable("logoBase64", data.logoBase64)

        return templateEngine.process("invoice", context)
    }
}
