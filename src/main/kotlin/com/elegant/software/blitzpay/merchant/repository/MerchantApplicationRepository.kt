package com.elegant.software.blitzpay.merchant.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantApplicationRepository : JpaRepository<MerchantApplication, UUID> {
    fun findByApplicationReference(applicationReference: String): MerchantApplication?

    fun existsByBusinessProfileRegistrationNumberAndStatusIn(
        registrationNumber: String,
        statuses: Collection<MerchantOnboardingStatus>
    ): Boolean
}
