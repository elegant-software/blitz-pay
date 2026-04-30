package com.elegant.software.blitzpay.merchant.api

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateProductRequest(
    val name: String,
    val branchId: java.util.UUID,
    val unitPrice: BigDecimal,
    val description: String? = null,
    val categoryId: UUID? = null,
    val productCode: Long? = null
)

data class UpdateProductRequest(
    val name: String,
    val branchId: java.util.UUID,
    val unitPrice: BigDecimal,
    val description: String? = null,
    val categoryId: UUID? = null,
    val productCode: Long? = null
)

data class ProductResponse(
    val productId: UUID,
    val branchId: UUID,
    val name: String,
    val description: String?,
    val unitPrice: BigDecimal,
    val imageUrl: String?,
    val active: Boolean,
    val status: String,
    val categoryId: UUID? = null,
    val categoryName: String? = null,
    val productCode: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class OrderableMerchantProduct(
    val productId: UUID,
    val merchantApplicationId: UUID,
    val branchId: UUID?,
    val name: String,
    val description: String?,
    val unitPrice: BigDecimal,
    val active: Boolean,
)
