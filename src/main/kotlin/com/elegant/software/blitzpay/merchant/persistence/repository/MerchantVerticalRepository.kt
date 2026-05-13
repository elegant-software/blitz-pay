package com.elegant.software.blitzpay.merchant.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantVertical
import org.springframework.data.jpa.repository.JpaRepository

interface MerchantVerticalRepository : JpaRepository<MerchantVertical, String> {
    fun findAllByActiveTrue(): List<MerchantVertical>
    fun existsByCodeAndActiveTrue(code: String): Boolean
}
