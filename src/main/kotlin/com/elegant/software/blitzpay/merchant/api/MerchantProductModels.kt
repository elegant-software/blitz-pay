package com.elegant.software.blitzpay.merchant.api

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateProductRequest(
    val name: String,
    val unitPrice: BigDecimal,
    val imageUrl: String? = null
)

data class UpdateProductRequest(
    val name: String,
    val unitPrice: BigDecimal,
    val imageUrl: String? = null
)

data class ProductResponse(
    val productId: UUID,
    val merchantId: UUID,
    val name: String,
    val unitPrice: BigDecimal,
    val imageUrl: String?,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ProductListResponse(
    val merchantId: UUID,
    val products: List<ProductResponse>
)
