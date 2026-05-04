package com.elegant.software.blitzpay.merchant.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_product_categories", schema = "blitzpay")
class MerchantProductCategory(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_application_id", nullable = false, updatable = false)
    val merchantApplicationId: UUID,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(name.trim().length <= 100) { "name must be <= 100 characters" }
        name = name.trim()
    }

    fun rename(newName: String, at: Instant = Instant.now()) {
        require(newName.isNotBlank()) { "name must not be blank" }
        require(newName.trim().length <= 100) { "name must be <= 100 characters" }
        name = newName.trim()
        updatedAt = at
    }
}
