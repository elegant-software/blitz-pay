package com.elegant.software.blitzpay.merchant.api

import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.ReviewOutcome
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Public API for the merchant onboarding module, exposed to other Spring Modulith modules.
 *
 * This interface provides a stable boundary for onboarding lifecycle operations
 * without exposing merchant application internals directly across module lines.
 */
@NamedInterface("MerchantGateway")
interface MerchantGateway {
    fun submit(applicationId: UUID, submittedAt: Instant = Instant.now()): MerchantSummary

    fun transition(
        applicationId: UUID,
        nextStatus: MerchantOnboardingStatus,
        changedAt: Instant = Instant.now()
    ): MerchantSummary

    fun requestChanges(
        applicationId: UUID,
        reason: String,
        changedAt: Instant = Instant.now()
    ): MerchantSummary

    fun recordDecision(
        applicationId: UUID,
        outcome: ReviewOutcome,
        reason: String,
        reviewerId: String,
        decidedAt: Instant = Instant.now()
    ): MerchantSummary

    fun startMonitoring(
        applicationId: UUID,
        triggerReason: String,
        changedAt: Instant = Instant.now()
    ): MerchantSummary
}

data class MerchantSummary(
    val applicationId: UUID,
    val applicationReference: String,
    val registrationNumber: String,
    val status: MerchantOnboardingStatus,
    val submittedAt: Instant?,
    val lastUpdatedAt: Instant
)
