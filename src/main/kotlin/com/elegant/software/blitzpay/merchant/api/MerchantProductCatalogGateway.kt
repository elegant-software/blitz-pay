package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

interface MerchantProductCatalogGateway {
    fun findActiveProducts(merchantId: UUID, branchId: UUID): List<CatalogProduct>
}
