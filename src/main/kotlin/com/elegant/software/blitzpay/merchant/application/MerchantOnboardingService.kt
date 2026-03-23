package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.merchant.api.MerchantSummary
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.MonitoringRecord
import com.elegant.software.blitzpay.merchant.domain.ReviewOutcome
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.support.MerchantObservabilitySupport
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class MerchantOnboardingService(
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val merchantApplicationValidator: MerchantApplicationValidator,
    private val merchantAuditTrail: MerchantAuditTrail,
    private val merchantObservabilitySupport: MerchantObservabilitySupport
) : MerchantGateway {

    override fun submit(applicationId: UUID, submittedAt: Instant): MerchantSummary {
        return observe("submit") {
            val application = getApplication(applicationId)
            merchantApplicationValidator.validateForSubmission(application)
            ensureNoDuplicateActiveApplication(application)
            application.submit(submittedAt)
            val saved = merchantApplicationRepository.save(application)
            audit(saved, SYSTEM_ACTOR, "submit", submittedAt)
            saved
        }.toSummary()
    }

    override fun transition(
        applicationId: UUID,
        nextStatus: MerchantOnboardingStatus,
        changedAt: Instant
    ): MerchantSummary {
        return observe("transition") {
            val application = getApplication(applicationId)
            application.transitionTo(nextStatus, changedAt)
            val saved = merchantApplicationRepository.save(application)
            audit(saved, SYSTEM_ACTOR, "transition", changedAt, mapOf("nextStatus" to nextStatus.name))
            saved
        }.toSummary()
    }

    override fun requestChanges(
        applicationId: UUID,
        reason: String,
        changedAt: Instant
    ): MerchantSummary {
        return observe("request_changes") {
            require(reason.isNotBlank()) { "Change request reason is required" }
            val application = getApplication(applicationId)
            application.requestChanges(reason, changedAt)
            val saved = merchantApplicationRepository.save(application)
            audit(saved, SYSTEM_ACTOR, "request_changes", changedAt, mapOf("reason" to reason))
            saved
        }.toSummary()
    }

    override fun recordDecision(
        applicationId: UUID,
        outcome: ReviewOutcome,
        reason: String,
        reviewerId: String,
        decidedAt: Instant
    ): MerchantSummary {
        return observe("record_decision") {
            require(reason.isNotBlank()) { "Decision reason is required" }
            require(reviewerId.isNotBlank()) { "Reviewer id is required" }
            val application = getApplication(applicationId)
            application.recordDecision(outcome, reason, reviewerId, decidedAt)
            val saved = merchantApplicationRepository.save(application)
            audit(saved, reviewerId, "record_decision", decidedAt, mapOf("outcome" to outcome.name))
            saved
        }.toSummary()
    }

    override fun startMonitoring(
        applicationId: UUID,
        triggerReason: String,
        changedAt: Instant
    ): MerchantSummary {
        return observe("start_monitoring") {
            require(triggerReason.isNotBlank()) { "Monitoring trigger reason is required" }
            val application = getApplication(applicationId)
            application.startMonitoring(
                MonitoringRecord(lastTriggerReason = triggerReason, lastReviewedAt = changedAt, createdAt = changedAt),
                changedAt
            )
            val saved = merchantApplicationRepository.save(application)
            audit(saved, SYSTEM_ACTOR, "start_monitoring", changedAt, mapOf("triggerReason" to triggerReason))
            saved
        }.toSummary()
    }

    private fun getApplication(applicationId: UUID): MerchantApplication =
        merchantApplicationRepository.findById(applicationId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $applicationId") }

    private fun ensureNoDuplicateActiveApplication(application: MerchantApplication) {
        if (!ACTIVE_APPLICATION_STATUSES.contains(application.status)) {
            val duplicateExists = merchantApplicationRepository.existsByBusinessProfileRegistrationNumberAndStatusIn(
                application.businessProfile.registrationNumber,
                ACTIVE_APPLICATION_STATUSES
            )
            require(!duplicateExists) {
                "An active merchant application already exists for registration number ${application.businessProfile.registrationNumber}"
            }
        }
    }

    private fun audit(
        application: MerchantApplication,
        actorId: String,
        action: String,
        occurredAt: Instant,
        details: Map<String, String> = emptyMap()
    ) {
        merchantAuditTrail.record(
            MerchantAuditEvent(
                applicationId = application.id,
                applicationReference = application.applicationReference,
                actorId = actorId,
                action = action,
                status = application.status,
                occurredAt = occurredAt,
                details = details
            )
        )
    }

    private fun observe(action: String, block: () -> MerchantApplication): MerchantApplication =
        try {
            block().also { merchantObservabilitySupport.recordSuccess(action, it.status) }
        } catch (ex: RuntimeException) {
            merchantObservabilitySupport.recordFailure(action, ex::class.simpleName ?: "runtime_exception")
            throw ex
        }

    private fun MerchantApplication.toSummary() = MerchantSummary(
        applicationId = id,
        applicationReference = applicationReference,
        registrationNumber = businessProfile.registrationNumber,
        status = status,
        submittedAt = submittedAt,
        lastUpdatedAt = lastUpdatedAt
    )

    companion object {
        private const val SYSTEM_ACTOR = "system"
        private val ACTIVE_APPLICATION_STATUSES = setOf(
            MerchantOnboardingStatus.SUBMITTED,
            MerchantOnboardingStatus.VERIFICATION,
            MerchantOnboardingStatus.SCREENING,
            MerchantOnboardingStatus.RISK_REVIEW,
            MerchantOnboardingStatus.DECISION_PENDING,
            MerchantOnboardingStatus.SETUP,
            MerchantOnboardingStatus.ACTIVE,
            MerchantOnboardingStatus.MONITORING,
            MerchantOnboardingStatus.ACTION_REQUIRED
        )
    }
}
