package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Invoices", description = "EU-standard ZUGFeRD / Factur-X invoice generation")
@RestController
@RequestMapping("/invoices")
class InvoiceController(private val invoiceService: InvoiceService) {

    @Operation(summary = "Generate ZUGFeRD XML invoice", description = "Returns a ZUGFeRD 2.0 / Factur-X compliant XML document. Send Accept: application/xml.")
    @PostMapping(produces = [MediaType.APPLICATION_XML_VALUE])
    fun generateXml(@RequestBody invoiceData: InvoiceData): ResponseEntity<ByteArray> {
        val xml = invoiceService.generateXml(invoiceData)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-${invoiceData.invoiceNumber}.xml\"")
            .contentType(MediaType.APPLICATION_XML)
            .body(xml)
    }

    @Operation(summary = "Generate ZUGFeRD PDF invoice", description = "Returns a human-readable PDF/A-3 with embedded ZUGFeRD XML. Send Accept: application/pdf.")
    @PostMapping(produces = [MediaType.APPLICATION_PDF_VALUE])
    fun generatePdf(@RequestBody invoiceData: InvoiceData): ResponseEntity<ByteArray> {
        val pdf = invoiceService.generatePdf(invoiceData)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-${invoiceData.invoiceNumber}.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
    }
}
