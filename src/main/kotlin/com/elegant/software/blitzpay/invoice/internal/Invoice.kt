package com.elegant.software.blitzpay.invoice.internal

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "invoices")
class Invoice(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /**
     * Stored in minor currency units to keep persistence exact and queryable.
     */
    @Column(name = "amount", nullable = false)
    val amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    var paymentStatus: PaymentStatus,

    @OneToMany(
        mappedBy = "invoice",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    val recipients: MutableList<InvoiceRecipient> = mutableListOf()
) {
    @PrePersist
    @PreUpdate
    private fun validateRecipients() {
        require(recipients.isNotEmpty()) { "Invoice must have at least one recipient" }
        recipients.forEach { recipient ->
            if (recipient.invoice !== this) {
                recipient.attachTo(this)
            }
        }
    }

    fun addRecipient(recipient: InvoiceRecipient) {
        recipients += recipient.attachTo(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Invoice) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
