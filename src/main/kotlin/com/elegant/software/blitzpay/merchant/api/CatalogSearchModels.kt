package com.elegant.software.blitzpay.merchant.api

import java.math.BigDecimal
import java.util.UUID

data class CatalogProduct(
    val productId: UUID,
    val branchId: UUID,
    val name: String,
    val description: String?,
    val unitPrice: BigDecimal,
    val imageUrl: String?,
)