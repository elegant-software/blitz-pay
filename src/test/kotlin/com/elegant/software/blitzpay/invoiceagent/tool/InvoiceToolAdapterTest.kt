package com.elegant.software.blitzpay.invoiceagent.tool

import com.elegant.software.blitzpay.invoice.api.InvoiceAnalysisService
import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceService
import com.elegant.software.blitzpay.invoice.api.InvoiceTotals
import com.elegant.software.blitzpay.invoice.api.InvoiceValidationResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class InvoiceToolAdapterTest {

    private val invoiceService = mock<InvoiceService>()
    private val invoiceAnalysisService = mock<InvoiceAnalysisService>()
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private val adapter = InvoiceToolAdapter(
        invoiceService = invoiceService,
        invoiceAnalysisService = invoiceAnalysisService,
        objectMapper = objectMapper
    )

    @Test
    fun `normalizeInvoiceInput returns failure for non-json payload`() {
        val result = adapter.normalizeInvoiceInput("not-json")

        assertFalse(result.success)
        assertTrue(result.error!!.contains("Unable to parse invoice payload"))
    }

    @Test
    fun `validateInvoice delegates to invoice analysis service`() {
        whenever(invoiceAnalysisService.validate(any<InvoiceData>()))
            .thenReturn(InvoiceValidationResult(valid = true, errors = emptyList()))

        val result = adapter.validateInvoice(validInvoiceJson())

        assertTrue(result.success)
        assertTrue(result.content.contains("\"valid\":true"))
    }

    @Test
    fun `calculateInvoiceTotals delegates to invoice analysis service`() {
        whenever(invoiceAnalysisService.calculateTotals(any<InvoiceData>()))
            .thenReturn(
                InvoiceTotals(
                    subtotal = BigDecimal("100.00"),
                    vatTotal = BigDecimal("19.00"),
                    grandTotal = BigDecimal("119.00"),
                    currency = "EUR"
                )
            )

        val result = adapter.calculateInvoiceTotals(validInvoiceJson())

        assertTrue(result.success)
        assertTrue(result.content.contains("119.00"))
    }

    private fun validInvoiceJson(): String =
        """
        {
          "invoiceNumber": "INV-1",
          "issueDate": "2026-03-01",
          "dueDate": "2026-03-31",
          "seller": {
            "name": "Seller Ltd",
            "street": "Seller Street 1",
            "zip": "10115",
            "city": "Berlin",
            "country": "DE",
            "vatId": "DE111"
          },
          "buyer": {
            "name": "Buyer GmbH",
            "street": "Buyer Street 2",
            "zip": "20095",
            "city": "Hamburg",
            "country": "DE",
            "vatId": "DE222"
          },
          "lineItems": [
            {
              "description": "Service",
              "quantity": 1,
              "unitPrice": 100.00,
              "vatPercent": 19
            }
          ],
          "currency": "EUR"
        }
        """.trimIndent()
}
