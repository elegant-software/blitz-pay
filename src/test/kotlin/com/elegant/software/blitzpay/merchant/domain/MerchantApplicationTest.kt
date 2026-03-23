package com.elegant.software.blitzpay.merchant.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MerchantApplicationTest {

    @Test
    fun `submit moves application from draft to submitted and stamps submission time`() {
        val application = merchantApplication()
        val submittedAt = Instant.parse("2026-03-23T10:15:30Z")

        application.submit(submittedAt)

        assertEquals(MerchantOnboardingStatus.SUBMITTED, application.status)
        assertEquals(submittedAt, application.submittedAt)
        assertEquals(submittedAt, application.lastUpdatedAt)
    }

    @Test
    fun `recording an approval moves application into setup and stores decision`() {
        val application = merchantApplication(status = MerchantOnboardingStatus.DECISION_PENDING)

        application.recordDecision(
            outcome = ReviewOutcome.APPROVED,
            reason = "compliance checks passed",
            reviewerId = "reviewer-1"
        )

        assertEquals(MerchantOnboardingStatus.SETUP, application.status)
        assertEquals(1, application.reviewDecisions.size)
        assertEquals(ReviewOutcome.APPROVED, application.reviewDecisions.single().outcome)
    }

    @Test
    fun `starting monitoring attaches record and moves application into monitoring`() {
        val application = merchantApplication(status = MerchantOnboardingStatus.ACTIVE)
        val monitoringRecord = MonitoringRecord(lastTriggerReason = "initial activation")

        application.startMonitoring(monitoringRecord)

        assertEquals(MerchantOnboardingStatus.MONITORING, application.status)
        assertNotNull(application.monitoringRecord)
        assertEquals(MonitoringStatus.ACTIVE, application.monitoringRecord?.status)
    }

    private fun merchantApplication(
        status: MerchantOnboardingStatus = MerchantOnboardingStatus.DRAFT
    ) = MerchantApplication(
        applicationReference = "MO-123456",
        businessProfile = BusinessProfile(
            legalBusinessName = "Acme GmbH",
            businessType = "LIMITED_COMPANY",
            registrationNumber = "HRB123456",
            operatingCountry = "DE",
            primaryBusinessAddress = "Alexanderplatz 1, Berlin"
        ),
        primaryContact = PrimaryContact(
            fullName = "Mina Example",
            email = "mina@example.com",
            phoneNumber = "+49123456789"
        ),
        status = status
    )
}
