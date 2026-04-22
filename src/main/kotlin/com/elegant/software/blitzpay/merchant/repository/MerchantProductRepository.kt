package com.elegant.software.blitzpay.merchant.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantProduct
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface MerchantProductRepository : JpaRepository<MerchantProduct, UUID> {
    fun findByNameAndMerchantApplicationIdAndMerchantBranchIdAndActiveTrue(
        name: String,
        merchantApplicationId: UUID,
        merchantBranchId: UUID
    ): MerchantProduct?

    fun findByNameAndMerchantApplicationIdAndMerchantBranchId(
        name: String,
        merchantApplicationId: UUID,
        merchantBranchId: UUID
    ): MerchantProduct?

    fun findAllByActiveTrue(): List<MerchantProduct>
    fun findAllByActiveTrueAndMerchantBranchId(merchantBranchId: UUID): List<MerchantProduct>
    fun findByIdAndActiveTrue(id: UUID): Optional<MerchantProduct>
    fun findByIdAndActiveTrueAndMerchantBranchId(id: UUID, merchantBranchId: UUID): Optional<MerchantProduct>
}
