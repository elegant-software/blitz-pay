package com.elegant.software.blitzpay.payments.invoice

import com.elegant.software.blitzpay.invoice.ZugferdInvoiceService
import com.elegant.software.blitzpay.invoice.api.BankAccountData
import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceLineItem
import com.elegant.software.blitzpay.support.TestFixtureLoader
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.math.BigDecimal

/**
 * Unit tests for [ZugferdInvoiceService].
 *
 * Validates that the service correctly generates EU-standard ZUGFeRD/Factur-X
 * invoices in both XML and PDF formats using the Thymeleaf template engine.
 */
class ZugferdInvoiceServiceTest {

    private lateinit var invoiceService: ZugferdInvoiceService

    private val sampleInvoice: InvoiceData = TestFixtureLoader.invoiceData()
    private val expectations = TestFixtureLoader.fixture.expectations

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
        assertTrue(xmlString.contains(expectations.xmlRootElement), "XML must contain CrossIndustryInvoice root element")
        assertTrue(xmlString.contains(expectations.invoiceNumber), "XML must contain the invoice number")
        assertTrue(xmlString.contains(expectations.sellerName), "XML must contain the seller name")
        assertTrue(xmlString.contains(expectations.buyerName), "XML must contain the buyer name")
    }

    @Test
    fun `generateXml includes line items`() {
        val xml = invoiceService.generateXml(sampleInvoice)
        val xmlString = String(xml, Charsets.UTF_8)

        assertTrue(xmlString.contains(expectations.firstLineItemDescription), "XML must contain first line item description")
        assertTrue(xmlString.contains(expectations.secondLineItemDescription), "XML must contain second line item description")
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

        assertTrue(invoice.number == expectations.invoiceNumber)
        assertTrue(invoice.sender.name == expectations.sellerName)
        assertTrue(invoice.recipient.name == expectations.buyerName)
        assertTrue(invoice.currency == expectations.currency)
    }

    @Test
    fun `generateXml with single line item`() {
        val singleItemInvoice = sampleInvoice.copy(
            lineItems = listOf(
                InvoiceLineItem(
                    description = expectations.singleLineItemDescription,
                    quantity = BigDecimal(expectations.singleLineItemQuantity),
                    unitPrice = BigDecimal(expectations.singleLineItemUnitPrice),
                    vatPercent = BigDecimal(expectations.singleLineItemVatPercent)
                )
            )
        )
        val xml = invoiceService.generateXml(singleItemInvoice)
        val xmlString = String(xml, Charsets.UTF_8)

        assertTrue(xmlString.contains(expectations.singleLineItemDescription), "XML must contain the line item description")
        assertTrue(xmlString.contains(expectations.xmlRootElement), "XML must be a valid ZUGFeRD document")
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
                bankName = expectations.bankName,
                iban = expectations.iban,
                bic = expectations.bic
            )
        )
        val html = invoiceService.renderHtml(invoiceWithBank)

        assertTrue(html.contains(expectations.bankName), "HTML must contain bank name")
        assertTrue(html.contains(expectations.iban), "HTML must contain IBAN")
        assertTrue(html.contains(expectations.bic), "HTML must contain BIC")
        assertTrue(html.contains("Payment Details"), "HTML must contain payment details section")
    }

    @Test
    fun `renderHtml includes footer text`() {
        val invoiceWithFooter = sampleInvoice.copy(
            footerText = expectations.footerText
        )
        val html = invoiceService.renderHtml(invoiceWithFooter)

        assertTrue(html.contains(expectations.footerText), "HTML must contain footer text")
    }

    @Test
    fun `renderHtml includes logo when provided`() {
        val invoiceWithLogo = sampleInvoice.copy(
            logoBase64 = expectations.logoBase64
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
                bankName = expectations.bankName,
                iban = expectations.iban,
                bic = expectations.bic
            ),
            footerText = expectations.footerText
        )
        val pdf = invoiceService.generatePdf(fullInvoice)

        assertNotNull(pdf)
        assertTrue(pdf.isNotEmpty(), "PDF output must not be empty")
        val header = String(pdf.copyOfRange(0, 5))
        assertTrue(header.startsWith("%PDF"), "Output must be a valid PDF document")
    }
}
