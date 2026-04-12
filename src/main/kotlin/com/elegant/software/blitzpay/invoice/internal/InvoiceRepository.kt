package com.elegant.software.blitzpay.invoice.internal

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface InvoiceRepository : JpaRepository<Invoice, UUID> {

    @Query("select i from Invoice i where i.id = :id")
    @EntityGraph(attributePaths = ["recipients"])
    fun findDetailedById(id: UUID): Invoice?
}
