package com.elegant.software.blitzpay.payments.invoice

import com.elegant.software.blitzpay.invoice.InvoiceController
import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(InvoiceController::class)
class InvoiceControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var invoiceService: InvoiceService

    private val sampleInvoiceJson = """
    {
        "invoiceNumber": "INV-2026-001",
        "issueDate": "2026-02-18",
        "dueDate": "2026-03-18",
        "seller": {
            "name": "BlitzPay GmbH",
            "street": "Musterstrasse 1",
            "zip": "10115",
            "city": "Berlin",
            "country": "DE",
            "vatId": "DE123456789"
        },
        "buyer": {
            "name": "Kunde AG",
            "street": "Beispielweg 42",
            "zip": "80331",
            "city": "Munich",
            "country": "DE",
            "vatId": "DE987654321"
        },
        "lineItems": [
            {
                "description": "Software License",
                "quantity": 2,
                "unitPrice": 150.00,
                "vatPercent": 19
            }
        ],
        "currency": "EUR"
    }
    """.trimIndent()

    @Test
    fun `POST invoices with Accept xml returns XML content`() {
        val xmlBytes = "<CrossIndustryInvoice>test</CrossIndustryInvoice>".toByteArray()
        whenever(invoiceService.generateXml(any<InvoiceData>())).thenReturn(xmlBytes)

        webTestClient.post()
            .uri("/v1/invoices")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_XML)
            .bodyValue(sampleInvoiceJson)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_XML)
            .expectHeader().valueEquals("Content-Disposition", "attachment; filename=\"invoice-INV-2026-001.xml\"")
            .expectBody()
            .xpath("/CrossIndustryInvoice").exists()
    }

    @Test
    fun `POST invoices with Accept pdf returns PDF content`() {
        val pdfBytes = "%PDF-1.4 fake content".toByteArray()
        whenever(invoiceService.generatePdf(any<InvoiceData>())).thenReturn(pdfBytes)

        webTestClient.post()
            .uri("/v1/invoices")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_PDF)
            .bodyValue(sampleInvoiceJson)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_PDF)
            .expectHeader().valueEquals("Content-Disposition", "attachment; filename=\"invoice-INV-2026-001.pdf\"")
            .expectBody()
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.isNotEmpty()) { "PDF response body must not be empty" }
            }
    }

    @Test
    fun `POST v1 invoices path routes to version 1 handler`() {
        val xmlBytes = "<CrossIndustryInvoice>test</CrossIndustryInvoice>".toByteArray()
        whenever(invoiceService.generateXml(any<InvoiceData>())).thenReturn(xmlBytes)

        webTestClient.post()
            .uri("/v1/invoices")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_XML)
            .bodyValue(sampleInvoiceJson)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_XML)
            .expectBody()
            .xpath("/CrossIndustryInvoice").exists()
    }
}
