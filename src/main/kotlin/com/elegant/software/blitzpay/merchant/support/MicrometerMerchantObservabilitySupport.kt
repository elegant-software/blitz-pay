package com.elegant.software.blitzpay.merchant.support

import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class MicrometerMerchantObservabilitySupport(
    private val meterRegistry: MeterRegistry
) : MerchantObservabilitySupport {
    private val logger = KotlinLogging.logger {}

    override fun recordSuccess(action: String, status: MerchantOnboardingStatus) {
        meterRegistry.counter(
            "merchant.onboarding.actions",
            "action", action,
            "status", status.name
        ).increment()

        logger.info { "merchant_observability action=$action status=${status.name} result=success" }
    }

    override fun recordFailure(action: String, reason: String) {
        meterRegistry.counter(
            "merchant.onboarding.failures",
            "action", action,
            "reason", reason
        ).increment()

        logger.warn { "merchant_observability action=$action result=failure reason=$reason" }
    }
}
