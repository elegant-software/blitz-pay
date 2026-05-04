package com.elegant.software.blitzpay.merchant.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantProductCategory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantProductCategoryRepository : JpaRepository<MerchantProductCategory, UUID> {
    fun findAllByMerchantApplicationId(merchantApplicationId: UUID): List<MerchantProductCategory>
    fun findByMerchantApplicationIdAndId(merchantApplicationId: UUID, id: UUID): MerchantProductCategory?
    fun findByMerchantApplicationIdAndNameIgnoreCase(
        merchantApplicationId: UUID,
        name: String
    ): MerchantProductCategory?

    fun existsByIdAndMerchantApplicationId(id: UUID, merchantApplicationId: UUID): Boolean
    fun existsByMerchantApplicationIdAndNameIgnoreCase(merchantApplicationId: UUID, name: String): Boolean
}
