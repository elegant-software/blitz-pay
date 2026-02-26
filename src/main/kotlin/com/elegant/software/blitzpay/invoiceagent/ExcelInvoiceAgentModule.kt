package com.elegant.software.blitzpay.invoiceagent

import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceLineItem
import com.elegant.software.blitzpay.invoice.api.InvoiceService
import com.elegant.software.blitzpay.invoice.api.TradePartyData
import dev.langchain4j.agent.tool.Tool
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.io.FileInputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class AgentRunState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

data class AgentRunStatus(
    val runId: UUID,
    val filePath: String,
    val state: AgentRunState,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val processedRows: Int = 0,
    val createdInvoices: Int = 0,
    val error: String? = null
)

data class StartAgentRequest(
    val filePath: String
)

@Component
class ExcelInvoiceAgentTools(
    private val invoiceService: InvoiceService
) {

    private val formatter = DataFormatter()

    @Tool("Read invoice rows from an Excel file and transform them to InvoiceData records")
    fun readInvoiceRows(filePath: String): List<InvoiceData> {
        val path = Path.of(filePath)
        require(Files.exists(path)) { "Excel file does not exist: $filePath" }

        FileInputStream(path.toFile()).use { input ->
            XSSFWorkbook(input).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                if (sheet.physicalNumberOfRows <= 1) return emptyList()

                return (1..sheet.lastRowNum)
                    .mapNotNull { rowIndex ->
                        val row = sheet.getRow(rowIndex) ?: return@mapNotNull null
                        val invoiceNumber = readCell(rowIndex, row.getCell(0))
                        if (invoiceNumber.isBlank()) return@mapNotNull null

                        InvoiceData(
                            invoiceNumber = invoiceNumber,
                            issueDate = LocalDate.parse(readCell(rowIndex, row.getCell(1))),
                            dueDate = LocalDate.parse(readCell(rowIndex, row.getCell(2))),
                            seller = TradePartyData(
                                name = readCell(rowIndex, row.getCell(3)),
                                street = readCell(rowIndex, row.getCell(4)),
                                zip = readCell(rowIndex, row.getCell(5)),
                                city = readCell(rowIndex, row.getCell(6)),
                                country = readCell(rowIndex, row.getCell(7)),
                                vatId = readOptionalCell(row.getCell(8))
                            ),
                            buyer = TradePartyData(
                                name = readCell(rowIndex, row.getCell(9)),
                                street = readCell(rowIndex, row.getCell(10)),
                                zip = readCell(rowIndex, row.getCell(11)),
                                city = readCell(rowIndex, row.getCell(12)),
                                country = readCell(rowIndex, row.getCell(13)),
                                vatId = readOptionalCell(row.getCell(14))
                            ),
                            lineItems = listOf(
                                InvoiceLineItem(
                                    description = readCell(rowIndex, row.getCell(15)),
                                    quantity = BigDecimal(readCell(rowIndex, row.getCell(16))),
                                    unitPrice = BigDecimal(readCell(rowIndex, row.getCell(17))),
                                    vatPercent = BigDecimal(readCell(rowIndex, row.getCell(18)))
                                )
                            ),
                            currency = readOptionalCell(row.getCell(19)) ?: "EUR"
                        )
                    }
            }
        }
    }

    @Tool("Create invoice XML and PDF using the invoice module")
    fun createInvoice(invoiceData: InvoiceData) {
        invoiceService.generateXml(invoiceData)
        invoiceService.generatePdf(invoiceData)
    }

    private fun readCell(rowIndex: Int, cell: org.apache.poi.ss.usermodel.Cell?): String {
        val value = readOptionalCell(cell)
        require(!value.isNullOrBlank()) { "Missing required cell in row $rowIndex" }
        return value
    }

    private fun readOptionalCell(cell: org.apache.poi.ss.usermodel.Cell?): String? {
        return cell?.let { formatter.formatCellValue(it).trim() }?.takeIf { it.isNotBlank() }
    }
}

@Service
class ExcelInvoiceAgentService(
    private val tools: ExcelInvoiceAgentTools
) {

    private val runs = ConcurrentHashMap<UUID, AgentRunStatus>()

    fun start(filePath: String): AgentRunStatus {
        val runId = UUID.randomUUID()
        val now = Instant.now()
        runs[runId] = AgentRunStatus(
            runId = runId,
            filePath = filePath,
            state = AgentRunState.PENDING,
            startedAt = now
        )

        Thread.startVirtualThread {
            runs.computeIfPresent(runId) { _, existing -> existing.copy(state = AgentRunState.RUNNING) }
            try {
                val rows = tools.readInvoiceRows(filePath)
                rows.forEach { tools.createInvoice(it) }
                runs.computeIfPresent(runId) { _, existing ->
                    existing.copy(
                        state = AgentRunState.COMPLETED,
                        endedAt = Instant.now(),
                        processedRows = rows.size,
                        createdInvoices = rows.size
                    )
                }
            } catch (ex: Exception) {
                runs.computeIfPresent(runId) { _, existing ->
                    existing.copy(
                        state = AgentRunState.FAILED,
                        endedAt = Instant.now(),
                        error = ex.message
                    )
                }
            }
        }

        return runs.getValue(runId)
    }

    fun getStatus(runId: UUID): AgentRunStatus {
        return runs[runId] ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Run $runId not found")
    }
}

@RestController
@RequestMapping("/v1/agents/invoice")
class InvoiceAgentController(
    private val excelInvoiceAgentService: ExcelInvoiceAgentService
) {

    @PostMapping
    fun startAgent(@RequestBody request: StartAgentRequest): ResponseEntity<AgentRunStatus> {
        val run = excelInvoiceAgentService.start(request.filePath)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(run)
    }

    @GetMapping("/{runId}")
    fun getRun(@PathVariable runId: UUID): AgentRunStatus {
        return excelInvoiceAgentService.getStatus(runId)
    }
}
