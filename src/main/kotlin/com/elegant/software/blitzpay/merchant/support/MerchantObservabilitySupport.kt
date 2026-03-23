package com.elegant.software.blitzpay.merchant.support

import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus

interface MerchantObservabilitySupport {
    fun recordSuccess(action: String, status: MerchantOnboardingStatus)

    fun recordFailure(action: String, reason: String)
}
