package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.MerchantBusinessProfileRequest
import com.elegant.software.blitzpay.merchant.api.MerchantPrimaryContactRequest
import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.support.MerchantObservabilitySupport
import com.elegant.software.blitzpay.merchant.support.MerchantTestFixtureLoader
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MerchantRegistrationServiceTest {

    private val repository = mock<MerchantApplicationRepository>()
    private val auditTrail = mock<MerchantAuditTrail>()
    private val observabilitySupport = mock<MerchantObservabilitySupport>()
    private val service = MerchantRegistrationService(repository, auditTrail, observabilitySupport)

    private val validRequest = RegisterMerchantRequest(
        businessProfile = MerchantBusinessProfileRequest(
            legalBusinessName = "Acme GmbH",
            businessType = "LLC",
            registrationNumber = "DE123456789",
            operatingCountry = "DE",
            primaryBusinessAddress = "Hauptstrasse 1, 10115 Berlin"
        ),
        primaryContact = MerchantPrimaryContactRequest(
            fullName = "Jane Doe",
            email = "jane@acme.de",
            phoneNumber = "+4930123456"
        )
    )

    @Test
    fun `register creates an ACTIVE merchant when no duplicate exists`() {
        whenever(repository.existsByBusinessProfileRegistrationNumberAndStatusIn(any(), any()))
            .thenReturn(false)
        whenever(repository.save(any<com.elegant.software.blitzpay.merchant.domain.MerchantApplication>())).thenAnswer { it.arguments[0] }

        val result = service.register(validRequest)

        assertEquals(MerchantOnboardingStatus.ACTIVE, result.status)
        assertTrue(result.applicationReference.startsWith("BLTZ-"))
        assertEquals("DE123456789", result.businessProfile.registrationNumber)
    }

    @Test
    fun `register rejects duplicate active registration number`() {
        whenever(repository.existsByBusinessProfileRegistrationNumberAndStatusIn(any(), any()))
            .thenReturn(true)

        assertFailsWith<IllegalArgumentException> {
            service.register(validRequest)
        }.also { ex ->
            assertTrue(ex.message!!.contains("DE123456789"))
        }
    }

    @Test
    fun `register records audit event on success`() {
        whenever(repository.existsByBusinessProfileRegistrationNumberAndStatusIn(any(), any()))
            .thenReturn(false)
        whenever(repository.save(any<com.elegant.software.blitzpay.merchant.domain.MerchantApplication>())).thenAnswer { it.arguments[0] }

        service.register(validRequest)

        val captor = argumentCaptor<MerchantAuditEvent>()
        verify(auditTrail).record(captor.capture())
        assertEquals("register_direct", captor.firstValue.action)
        assertEquals(MerchantOnboardingStatus.ACTIVE, captor.firstValue.status)
    }

    @Test
    fun `findById throws when merchant does not exist`() {
        val unknownId = UUID.randomUUID()
        whenever(repository.findById(unknownId)).thenReturn(Optional.empty())

        assertFailsWith<NoSuchElementException> {
            service.findById(unknownId)
        }
    }

    @Test
    fun `findById returns merchant when it exists`() {
        val application = MerchantTestFixtureLoader.merchantApplication()
        whenever(repository.findById(application.id)).thenReturn(Optional.of(application))

        val result = service.findById(application.id)

        assertEquals(application.id, result.id)
    }
}
