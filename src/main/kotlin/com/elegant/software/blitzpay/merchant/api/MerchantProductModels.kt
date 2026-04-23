package com.elegant.software.blitzpay.merchant.api

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateProductRequest(
    val name: String,
    val branchId: java.util.UUID,
    val unitPrice: BigDecimal,
    val description: String? = null
)

data class UpdateProductRequest(
    val name: String,
    val branchId: java.util.UUID,
    val unitPrice: BigDecimal,
    val description: String? = null
)

data class ProductResponse(
    val productId: UUID,
    val branchId: UUID,
    val name: String,
    val description: String?,
    val unitPrice: BigDecimal,
    val imageUrl: String?,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
