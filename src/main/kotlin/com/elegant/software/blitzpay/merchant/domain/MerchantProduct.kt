package com.elegant.software.blitzpay.merchant.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.FilterDef
import org.hibernate.annotations.ParamDef
import org.hibernate.type.descriptor.java.UUIDJavaType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@FilterDef(
    name = "tenantFilter",
    parameters = [ParamDef(name = "merchantId", type = UUIDJavaType::class)]
)
@Filter(name = "tenantFilter", condition = "merchant_application_id = :merchantId")
@Entity
@Table(name = "merchant_products", schema = "blitzpay")
class MerchantProduct(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_application_id", nullable = false, updatable = false)
    val merchantApplicationId: UUID,

    @Column(nullable = false)
    var name: String,

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 4)
    var unitPrice: BigDecimal,

    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
) {
    fun deactivate(at: Instant = Instant.now()) {
        active = false
        updatedAt = at
    }

    fun update(name: String, unitPrice: BigDecimal, imageUrl: String?, at: Instant = Instant.now()) {
        require(unitPrice >= BigDecimal.ZERO) { "unitPrice must be >= 0" }
        this.name = name
        this.unitPrice = unitPrice
        this.imageUrl = imageUrl
        this.updatedAt = at
    }
}
