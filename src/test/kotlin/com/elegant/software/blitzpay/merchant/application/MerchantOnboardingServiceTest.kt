package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.Person
import com.elegant.software.blitzpay.merchant.domain.PersonRole
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.domain.ReviewOutcome
import com.elegant.software.blitzpay.merchant.domain.SupportingMaterial
import com.elegant.software.blitzpay.merchant.domain.SupportingMaterialType
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.support.MerchantObservabilitySupport
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MerchantOnboardingServiceTest {

    private val repository = mock<MerchantApplicationRepository>()
    private val auditTrail = mock<MerchantAuditTrail>()
    private val observabilitySupport = mock<MerchantObservabilitySupport>()
    private val validator = MerchantApplicationValidator()
    private val service = MerchantOnboardingService(repository, validator, auditTrail, observabilitySupport)

    @Test
    fun `submit validates application and persists submitted status`() {
        val application = readyToSubmitApplication()
        whenever(repository.findById(application.id)).thenReturn(Optional.of(application))
        whenever(
            repository.existsByBusinessProfileRegistrationNumberAndStatusIn(
                eq(application.businessProfile.registrationNumber),
                any()
            )
        ).thenReturn(false)
        whenever(repository.save(any<MerchantApplication>())).thenAnswer { it.getArgument<MerchantApplication>(0) }

        val submittedAt = Instant.parse("2026-03-23T14:00:00Z")
        val result = service.submit(application.id, submittedAt)

        assertEquals(MerchantOnboardingStatus.SUBMITTED, result.status)
        assertEquals(submittedAt, result.submittedAt)
        assertEquals(application.id, result.applicationId)
        verify(repository).save(application)
        verify(auditTrail).record(any())
        verify(observabilitySupport).recordSuccess("submit", MerchantOnboardingStatus.SUBMITTED)
    }

    @Test
    fun `submit rejects duplicate active application`() {
        val application = readyToSubmitApplication()
        whenever(repository.findById(application.id)).thenReturn(Optional.of(application))
        whenever(
            repository.existsByBusinessProfileRegistrationNumberAndStatusIn(
                eq(application.businessProfile.registrationNumber),
                any()
            )
        ).thenReturn(true)

        assertFailsWith<IllegalArgumentException> {
            service.submit(application.id)
        }

        verify(repository, never()).save(any())
        verify(observabilitySupport).recordFailure("submit", "IllegalArgumentException")
    }

    @Test
    fun `record decision moves approved application into setup`() {
        val application = readyToSubmitApplication(status = MerchantOnboardingStatus.DECISION_PENDING)
        whenever(repository.findById(application.id)).thenReturn(Optional.of(application))
        whenever(repository.save(any<MerchantApplication>())).thenAnswer { it.getArgument<MerchantApplication>(0) }

        val result = service.recordDecision(
            applicationId = application.id,
            outcome = ReviewOutcome.APPROVED,
            reason = "checks passed",
            reviewerId = "ops-1"
        )

        assertEquals(MerchantOnboardingStatus.SETUP, result.status)
        assertEquals(application.applicationReference, result.applicationReference)
        verify(auditTrail).record(any())
        verify(observabilitySupport).recordSuccess("record_decision", MerchantOnboardingStatus.SETUP)
    }

    @Test
    fun `start monitoring creates monitoring record and persists application`() {
        val application = readyToSubmitApplication(status = MerchantOnboardingStatus.ACTIVE)
        whenever(repository.findById(application.id)).thenReturn(Optional.of(application))
        whenever(repository.save(any<MerchantApplication>())).thenAnswer { it.getArgument<MerchantApplication>(0) }

        val result = service.startMonitoring(application.id, "initial activation")

        assertEquals(MerchantOnboardingStatus.MONITORING, result.status)
        assertEquals(application.applicationReference, result.applicationReference)
        assertNotNull(result.lastUpdatedAt)
        verify(auditTrail).record(any())
        verify(observabilitySupport).recordSuccess("start_monitoring", MerchantOnboardingStatus.MONITORING)
    }

    private fun readyToSubmitApplication(
        status: MerchantOnboardingStatus = MerchantOnboardingStatus.DRAFT
    ) = MerchantApplication(
        applicationReference = "MO-SVC-1",
        businessProfile = BusinessProfile(
            legalBusinessName = "Acme GmbH",
            businessType = "LIMITED_COMPANY",
            registrationNumber = "HRB654321",
            operatingCountry = "DE",
            primaryBusinessAddress = "Alexanderplatz 1, Berlin"
        ),
        primaryContact = PrimaryContact(
            fullName = "Mina Example",
            email = "mina@example.com",
            phoneNumber = "+49123456789"
        ),
        status = status
    ).apply {
        addPerson(
            Person(
                fullName = "Mina Example",
                role = PersonRole.BENEFICIAL_OWNER,
                countryOfResidence = "DE",
                ownershipPercentage = 100
            )
        )
        addSupportingMaterial(
            SupportingMaterial(
                type = SupportingMaterialType.BUSINESS_REGISTRATION,
                fileName = "registration.pdf",
                storageKey = "merchant/MO-SVC-1/registration.pdf"
            )
        )
    }
}
