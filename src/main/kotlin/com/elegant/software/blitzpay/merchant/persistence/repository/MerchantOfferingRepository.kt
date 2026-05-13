package com.elegant.software.blitzpay.merchant.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantOffering
import org.springframework.data.jpa.repository.JpaRepository

interface MerchantOfferingRepository : JpaRepository<MerchantOffering, String> {
    fun findAllByActiveTrueOrderByDisplayNameAsc(): List<MerchantOffering>
}
