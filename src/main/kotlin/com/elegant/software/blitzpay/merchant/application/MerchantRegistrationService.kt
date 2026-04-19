package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.support.MerchantObservabilitySupport
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class MerchantRegistrationService(
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val merchantAuditTrail: MerchantAuditTrail,
    private val merchantObservabilitySupport: MerchantObservabilitySupport
) {

    private val log = LoggerFactory.getLogger(MerchantRegistrationService::class.java)

    fun register(request: RegisterMerchantRequest): MerchantApplication {
        val registrationNumber = request.businessProfile.registrationNumber
        val duplicateExists = merchantApplicationRepository.existsByBusinessProfileRegistrationNumberAndStatusIn(
            registrationNumber,
            ACTIVE_STATUSES
        )
        require(!duplicateExists) {
            "An active merchant application already exists for registration number $registrationNumber"
        }

        val applicationReference = "BLTZ-" + UUID.randomUUID().toString().take(8).uppercase()
        val now = Instant.now()

        val application = MerchantApplication(
            applicationReference = applicationReference,
            businessProfile = BusinessProfile(
                legalBusinessName = request.businessProfile.legalBusinessName,
                businessType = request.businessProfile.businessType,
                registrationNumber = registrationNumber,
                operatingCountry = request.businessProfile.operatingCountry,
                primaryBusinessAddress = request.businessProfile.primaryBusinessAddress
            ),
            primaryContact = PrimaryContact(
                fullName = request.primaryContact.fullName,
                email = request.primaryContact.email,
                phoneNumber = request.primaryContact.phoneNumber
            )
        )
        application.registerDirect(now)

        val saved = merchantApplicationRepository.save(application)
        merchantAuditTrail.record(
            MerchantAuditEvent(
                applicationId = saved.id,
                applicationReference = saved.applicationReference,
                actorId = SYSTEM_ACTOR,
                action = "register_direct",
                status = saved.status,
                occurredAt = now
            )
        )
        merchantObservabilitySupport.recordSuccess("register_direct", saved.status)
        log.info("Merchant registered directly: ref={} status={}", saved.applicationReference, saved.status)
        return saved
    }

    @Transactional(readOnly = true)
    fun findById(merchantId: UUID): MerchantApplication =
        merchantApplicationRepository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $merchantId") }

    companion object {
        private const val SYSTEM_ACTOR = "system"
        private val ACTIVE_STATUSES = setOf(
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
