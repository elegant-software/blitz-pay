package com.elegant.software.blitzpay.invoiceagent

import com.elegant.software.blitzpay.invoice.api.InvoiceService
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.io.FileOutputStream
import java.nio.file.Files
import kotlin.test.assertEquals

class ExcelInvoiceAgentToolsTest {

    private val invoiceService: InvoiceService = mock()
    private val tools = ExcelInvoiceAgentTools(invoiceService)

    @Test
    fun `reads invoice rows from excel`() {
        val tempFile = Files.createTempFile("invoice-agent", ".xlsx").toFile()

        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Invoices")
            sheet.createRow(0).apply {
                for (i in 0..19) createCell(i).setCellValue("h$i")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("INV-100")
                createCell(1).setCellValue("2026-01-01")
                createCell(2).setCellValue("2026-01-31")
                createCell(3).setCellValue("Seller Ltd")
                createCell(4).setCellValue("Seller Street")
                createCell(5).setCellValue("12345")
                createCell(6).setCellValue("Berlin")
                createCell(7).setCellValue("DE")
                createCell(9).setCellValue("Buyer Ltd")
                createCell(10).setCellValue("Buyer Street")
                createCell(11).setCellValue("54321")
                createCell(12).setCellValue("Munich")
                createCell(13).setCellValue("DE")
                createCell(15).setCellValue("Consulting")
                createCell(16).setCellValue("2")
                createCell(17).setCellValue("100.50")
                createCell(18).setCellValue("19")
                createCell(19).setCellValue("EUR")
            }

            FileOutputStream(tempFile).use { workbook.write(it) }
        }

        val rows = tools.readInvoiceRows(tempFile.absolutePath)

        assertEquals(1, rows.size)
        assertEquals("INV-100", rows.first().invoiceNumber)
        assertEquals("Consulting", rows.first().lineItems.first().description)
    }
}
