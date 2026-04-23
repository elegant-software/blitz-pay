package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.UpdateMerchantRequest
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.MerchantPaymentChannel
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertEquals

class MerchantManagementServiceTest {

    private val repository = mock<MerchantApplicationRepository>()
    private val storageService = mock<StorageService>()
    private val service = MerchantManagementService(repository, storageService)

    @Test
    fun `update merchant changes editable fields and status`() {
        val application = MerchantApplication(
            applicationReference = "BLTZ-123",
            businessProfile = BusinessProfile(
                legalBusinessName = "Old Name GmbH",
                businessType = "LLC",
                registrationNumber = "DE-123",
                operatingCountry = "DE",
                primaryBusinessAddress = "Old Address 1"
            ),
            primaryContact = PrimaryContact(
                fullName = "Old Contact",
                email = "old@example.com",
                phoneNumber = "+49111111111"
            ),
            status = MerchantOnboardingStatus.DRAFT
        )
        whenever(repository.findById(application.id)).thenReturn(Optional.of(application))
        whenever(repository.save(any<MerchantApplication>())).thenAnswer { it.getArgument<MerchantApplication>(0) }

        val response = service.update(
            application.id,
            UpdateMerchantRequest(
                legalBusinessName = "New Name GmbH",
                primaryBusinessAddress = "New Address 99",
                contactFullName = "New Contact",
                contactEmail = "new@example.com",
                contactPhoneNumber = "+49222222222",
                activePaymentChannels = setOf(MerchantPaymentChannel.STRIPE, MerchantPaymentChannel.TRUELAYER),
                status = MerchantOnboardingStatus.ACTIVE
            )
        )

        assertEquals("New Name GmbH", response.legalBusinessName)
        assertEquals("New Address 99", response.primaryBusinessAddress)
        assertEquals("New Contact", response.contactFullName)
        assertEquals("new@example.com", response.contactEmail)
        assertEquals(setOf(MerchantPaymentChannel.STRIPE, MerchantPaymentChannel.TRUELAYER), response.activePaymentChannels)
        assertEquals(MerchantOnboardingStatus.ACTIVE, response.status)
    }
}
