package com.elegant.software.blitzpay.merchant.domain

import com.elegant.software.blitzpay.merchant.support.MerchantTestFixtureLoader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MerchantApplicationTest {

    @Test
    fun `registerDirect sets status to ACTIVE and records activatedAt`() {
        val application = MerchantTestFixtureLoader.merchantApplication()
        val activatedAt = Instant.parse("2026-03-29T10:00:00Z")

        application.registerDirect(activatedAt)

        assertEquals(MerchantOnboardingStatus.ACTIVE, application.status)
        assertEquals(activatedAt, application.submittedAt)
        assertEquals(activatedAt, application.lastUpdatedAt)
    }

    @Test
    fun `registerDirect rejects transition from non-DRAFT status`() {
        val application = MerchantTestFixtureLoader.merchantApplication(status = MerchantOnboardingStatus.SUBMITTED)

        val ex = assertThrows<IllegalArgumentException> {
            application.registerDirect()
        }
        assert(ex.message!!.contains("SUBMITTED"))
    }

    @Test
    fun `submit moves application from draft to submitted and stamps submission time`() {
        val application = MerchantTestFixtureLoader.merchantApplication()
        val submittedAt = Instant.parse("2026-03-23T10:15:30Z")

        application.submit(submittedAt)

        assertEquals(MerchantOnboardingStatus.SUBMITTED, application.status)
        assertEquals(submittedAt, application.submittedAt)
        assertEquals(submittedAt, application.lastUpdatedAt)
    }

    @Test
    fun `recording an approval moves application into setup and stores decision`() {
        val application = MerchantTestFixtureLoader.merchantApplication(status = MerchantOnboardingStatus.DECISION_PENDING)

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
        val application = MerchantTestFixtureLoader.merchantApplication(status = MerchantOnboardingStatus.ACTIVE)
        val monitoringRecord = MonitoringRecord(lastTriggerReason = "initial activation")

        application.startMonitoring(monitoringRecord)

        assertEquals(MerchantOnboardingStatus.MONITORING, application.status)
        assertNotNull(application.monitoringRecord)
        assertEquals(MonitoringStatus.ACTIVE, application.monitoringRecord?.status)
    }
}
