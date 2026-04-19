package com.elegant.software.blitzpay.merchant.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantProduct
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface MerchantProductRepository : JpaRepository<MerchantProduct, UUID> {
    fun findAllByActiveTrue(): List<MerchantProduct>
    fun findByIdAndActiveTrue(id: UUID): Optional<MerchantProduct>
}
