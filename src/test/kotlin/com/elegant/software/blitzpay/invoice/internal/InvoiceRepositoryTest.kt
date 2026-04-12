package com.elegant.software.blitzpay.invoice.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InvoiceRepositoryTest {

    @Autowired
    private lateinit var invoiceRepository: InvoiceRepository

    @Test
    fun `stores invoice with person recipient`() {
        val invoice = Invoice(
            amount = 12_500,
            paymentStatus = PaymentStatus.PENDING,
            recipients = mutableListOf(
                InvoiceRecipient(
                    recipientType = RecipientType.PERSON,
                    displayName = "Ava Stone",
                    email = "ava.stone@example.com",
                    customerReference = "CUST-1001"
                )
            )
        )

        val saved = invoiceRepository.saveAndFlush(invoice)

        val loaded = invoiceRepository.findDetailedById(saved.id)

        assertNotNull(loaded)
        assertEquals(invoice.id, loaded!!.id)
        assertEquals(PaymentStatus.PENDING, loaded.paymentStatus)
        assertEquals(1, loaded.recipients.size)
        assertEquals(RecipientType.PERSON, loaded.recipients.single().recipientType)
        assertEquals("ava.stone@example.com", loaded.recipients.single().email)
    }

    @Test
    fun `stores invoice with group recipient`() {
        val accountingGroupId = UUID.randomUUID()
        val invoice = Invoice(
            amount = 47_900,
            paymentStatus = PaymentStatus.PAID,
            recipients = mutableListOf(
                InvoiceRecipient(
                    recipientType = RecipientType.GROUP,
                    displayName = "Northwind Finance",
                    groupId = accountingGroupId,
                    groupName = "Northwind Finance Team",
                    customerReference = "GROUP-44"
                )
            )
        )

        val saved = invoiceRepository.saveAndFlush(invoice)

        val loaded = invoiceRepository.findDetailedById(saved.id)

        assertNotNull(loaded)
        assertEquals(PaymentStatus.PAID, loaded!!.paymentStatus)
        assertEquals(RecipientType.GROUP, loaded.recipients.single().recipientType)
        assertEquals(accountingGroupId, loaded.recipients.single().groupId)
        assertEquals("Northwind Finance Team", loaded.recipients.single().groupName)
    }

    @Test
    fun `stores invoice with multiple recipients`() {
        val invoice = Invoice(
            amount = 88_000,
            paymentStatus = PaymentStatus.RECEIVED,
            recipients = mutableListOf(
                InvoiceRecipient(
                    recipientType = RecipientType.PERSON,
                    displayName = "Mina Patel",
                    email = "mina.patel@example.com"
                ),
                InvoiceRecipient(
                    recipientType = RecipientType.GROUP,
                    displayName = "Shared Services",
                    groupName = "Shared Services Group"
                )
            )
        )

        val saved = invoiceRepository.saveAndFlush(invoice)

        val loaded = invoiceRepository.findDetailedById(saved.id)

        assertNotNull(loaded)
        assertEquals(2, loaded!!.recipients.size)
        assertEquals(setOf(RecipientType.PERSON, RecipientType.GROUP), loaded.recipients.map { it.recipientType }.toSet())
        assertEquals(saved.id, loaded.recipients.first().invoice!!.id)
        assertEquals(saved.id, loaded.recipients.last().invoice!!.id)
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17-alpine")
    }
}
