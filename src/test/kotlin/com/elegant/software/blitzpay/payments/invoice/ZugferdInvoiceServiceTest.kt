package com.elegant.software.blitzpay.payments.invoice

import com.elegant.software.blitzpay.invoice.ZugferdInvoiceService
import com.elegant.software.blitzpay.invoice.api.BankAccountData
import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceLineItem
import com.elegant.software.blitzpay.invoice.api.TradePartyData
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for [ZugferdInvoiceService].
 *
 * Validates that the service correctly generates EU-standard ZUGFeRD/Factur-X
 * invoices in both XML and PDF formats using the Thymeleaf template engine.
 */
class ZugferdInvoiceServiceTest {

    private lateinit var invoiceService: ZugferdInvoiceService

    private val sampleInvoice = InvoiceData(
        invoiceNumber = "INV-2026-001",
        issueDate = LocalDate.of(2026, 2, 18),
        dueDate = LocalDate.of(2026, 3, 18),
        seller = TradePartyData(
            name = "BlitzPay GmbH",
            street = "Musterstrasse 1",
            zip = "10115",
            city = "Berlin",
            country = "DE",
            vatId = "DE123456789"
        ),
        buyer = TradePartyData(
            name = "Kunde AG",
            street = "Beispielweg 42",
            zip = "80331",
            city = "Munich",
            country = "DE",
            vatId = "DE987654321"
        ),
        lineItems = listOf(
            InvoiceLineItem(
                description = "Software License",
                quantity = BigDecimal("2"),
                unitPrice = BigDecimal("150.00"),
                vatPercent = BigDecimal("19")
            ),
            InvoiceLineItem(
                description = "Consulting Service",
                quantity = BigDecimal("5"),
                unitPrice = BigDecimal("200.00"),
                vatPercent = BigDecimal("19")
            )
        ),
        currency = "EUR"
    )

    @BeforeEach
    fun setUp() {
        val resolver = ClassLoaderTemplateResolver()
        resolver.prefix = "templates/"
        resolver.suffix = ".html"
        resolver.setTemplateMode("HTML")
        resolver.characterEncoding = "UTF-8"
        val templateEngine = SpringTemplateEngine()
        templateEngine.setTemplateResolver(resolver)
        invoiceService = ZugferdInvoiceService(templateEngine)
    }

    @Test
    fun `generateXml produces valid ZUGFeRD XML`() {
        val xml = invoiceService.generateXml(sampleInvoice)

        assertNotNull(xml)
        assertTrue(xml.isNotEmpty(), "XML output must not be empty")

        val xmlString = String(xml, Charsets.UTF_8)
        assertTrue(xmlString.contains("CrossIndustryInvoice"), "XML must contain CrossIndustryInvoice root element")
        assertTrue(xmlString.contains("INV-2026-001"), "XML must contain the invoice number")
        assertTrue(xmlString.contains("BlitzPay GmbH"), "XML must contain the seller name")
        assertTrue(xmlString.contains("Kunde AG"), "XML must contain the buyer name")
    }

    @Test
    fun `generateXml includes line items`() {
        val xml = invoiceService.generateXml(sampleInvoice)
        val xmlString = String(xml, Charsets.UTF_8)

        assertTrue(xmlString.contains("Software License"), "XML must contain first line item description")
        assertTrue(xmlString.contains("Consulting Service"), "XML must contain second line item description")
    }

    @Test
    fun `generatePdf produces valid PDF`() {
        val pdf = invoiceService.generatePdf(sampleInvoice)

        assertNotNull(pdf)
        assertTrue(pdf.isNotEmpty(), "PDF output must not be empty")
        assertTrue(pdf.size > 100, "PDF should be a reasonable size")
        // PDF files start with %PDF
        val header = String(pdf.copyOfRange(0, 5))
        assertTrue(header.startsWith("%PDF"), "Output must be a valid PDF document")
    }

    @Test
    fun `generatePdf embeds ZUGFeRD XML`() {
        val pdf = invoiceService.generatePdf(sampleInvoice)

        // The embedded ZUGFeRD XML filename should appear in the PDF
        val pdfString = String(pdf, Charsets.ISO_8859_1)
        assertTrue(
            pdfString.contains("factur-x.xml") || pdfString.contains("zugferd-invoice.xml") || pdfString.contains("ZUGFeRD"),
            "PDF must contain embedded ZUGFeRD XML attachment"
        )
    }

    @Test
    fun `toMustangInvoice maps data correctly`() {
        val invoice = invoiceService.toMustangInvoice(sampleInvoice)

        assertTrue(invoice.number == "INV-2026-001")
        assertTrue(invoice.sender.name == "BlitzPay GmbH")
        assertTrue(invoice.recipient.name == "Kunde AG")
        assertTrue(invoice.currency == "EUR")
    }

    @Test
    fun `generateXml with single line item`() {
        val singleItemInvoice = sampleInvoice.copy(
            lineItems = listOf(
                InvoiceLineItem(
                    description = "Single Item",
                    quantity = BigDecimal("1"),
                    unitPrice = BigDecimal("99.99"),
                    vatPercent = BigDecimal("19")
                )
            )
        )
        val xml = invoiceService.generateXml(singleItemInvoice)
        val xmlString = String(xml, Charsets.UTF_8)

        assertTrue(xmlString.contains("Single Item"), "XML must contain the line item description")
        assertTrue(xmlString.contains("CrossIndustryInvoice"), "XML must be a valid ZUGFeRD document")
    }

    @Test
    fun `renderBasePdf creates valid PDF`() {
        val pdf = invoiceService.renderBasePdf(sampleInvoice)

        assertNotNull(pdf)
        assertTrue(pdf.isNotEmpty())
        val header = String(pdf.copyOfRange(0, 5))
        assertTrue(header.startsWith("%PDF"), "Rendered base must be a valid PDF")
    }

    @Test
    fun `renderHtml includes bank account details`() {
        val invoiceWithBank = sampleInvoice.copy(
            bankAccount = BankAccountData(
                bankName = "Deutsche Bank",
                iban = "DE89 3704 0044 0532 0130 00",
                bic = "DEUTDEDB"
            )
        )
        val html = invoiceService.renderHtml(invoiceWithBank)

        assertTrue(html.contains("Deutsche Bank"), "HTML must contain bank name")
        assertTrue(html.contains("DE89 3704 0044 0532 0130 00"), "HTML must contain IBAN")
        assertTrue(html.contains("DEUTDEDB"), "HTML must contain BIC")
        assertTrue(html.contains("Payment Details"), "HTML must contain payment details section")
    }

    @Test
    fun `renderHtml includes footer text`() {
        val invoiceWithFooter = sampleInvoice.copy(
            footerText = "Thank you for your business!"
        )
        val html = invoiceService.renderHtml(invoiceWithFooter)

        assertTrue(html.contains("Thank you for your business!"), "HTML must contain footer text")
    }

    @Test
    fun `renderHtml includes logo when provided`() {
        val invoiceWithLogo = sampleInvoice.copy(
            logoBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        )
        val html = invoiceService.renderHtml(invoiceWithLogo)

        assertTrue(html.contains("data:image/png;base64,"), "HTML must contain base64 logo data URI")
    }

    @Test
    fun `renderHtml omits optional sections when not provided`() {
        val html = invoiceService.renderHtml(sampleInvoice)

        assertTrue(!html.contains("Payment Details"), "HTML must not contain bank account section when not provided")
        assertTrue(!html.contains("data:image/png;base64,"), "HTML must not contain logo when not provided")
    }

    @Test
    fun `generatePdf with bank account and footer produces valid PDF`() {
        val fullInvoice = sampleInvoice.copy(
            bankAccount = BankAccountData(
                bankName = "Deutsche Bank",
                iban = "DE89 3704 0044 0532 0130 00",
                bic = "DEUTDEDB"
            ),
            footerText = "Thank you for your business!"
        )
        val pdf = invoiceService.generatePdf(fullInvoice)

        assertNotNull(pdf)
        assertTrue(pdf.isNotEmpty(), "PDF output must not be empty")
        val header = String(pdf.copyOfRange(0, 5))
        assertTrue(header.startsWith("%PDF"), "Output must be a valid PDF document")
    }
}
