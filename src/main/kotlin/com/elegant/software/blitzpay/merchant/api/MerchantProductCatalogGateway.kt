package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

interface MerchantProductCatalogGateway {
    fun findActiveProducts(merchantId: UUID, branchId: UUID): List<CatalogProduct>
    fun findActiveProductsBySubject(subject: String): List<CatalogProduct>
    fun searchActiveProducts(searchText: String, limit: Int = 25): List<CatalogProduct>
}
