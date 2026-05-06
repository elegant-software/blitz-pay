package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.MerchantNameUpdated
import com.elegant.software.blitzpay.merchant.api.UpdateMerchantRequest
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.MerchantPaymentChannel
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.domain.MonitoringRecord
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.repository.MonitoringRecordRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class MerchantManagementServiceTest {

    private val repository = mock<MerchantApplicationRepository>()
    private val branchRepository = mock<MerchantBranchRepository>()
    private val monitoringRecordRepository = mock<MonitoringRecordRepository>()
    private val storageService = mock<StorageService>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val service = MerchantManagementService(repository, branchRepository, monitoringRecordRepository, storageService, eventPublisher)

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

    @Test
    fun `update publishes MerchantNameUpdated when name changes`() {
        val application = MerchantApplication(
            applicationReference = "BLTZ-EVT",
            businessProfile = BusinessProfile(
                legalBusinessName = "Old Name GmbH",
                businessType = "LLC",
                registrationNumber = "DE-EVT",
                operatingCountry = "DE",
                primaryBusinessAddress = "Event Street 1"
            ),
            primaryContact = PrimaryContact(
                fullName = "Contact",
                email = "evt@example.com",
                phoneNumber = "+490000"
            )
        )
        whenever(repository.findById(application.id)).thenReturn(Optional.of(application))
        whenever(repository.save(any<MerchantApplication>())).thenAnswer { it.getArgument<MerchantApplication>(0) }

        service.update(
            application.id,
            UpdateMerchantRequest(
                legalBusinessName = "New Name GmbH",
                primaryBusinessAddress = "Event Street 1",
                contactFullName = "Contact",
                contactEmail = "evt@example.com",
                contactPhoneNumber = "+490000",
                activePaymentChannels = emptySet()
            )
        )

        val captor = argumentCaptor<MerchantNameUpdated>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(application.id, captor.firstValue.merchantId)
        assertEquals("New Name GmbH", captor.firstValue.newName)
    }

    @Test
    fun `update does not publish MerchantNameUpdated when name unchanged`() {
        val application = MerchantApplication(
            applicationReference = "BLTZ-SAME",
            businessProfile = BusinessProfile(
                legalBusinessName = "Same Name GmbH",
                businessType = "LLC",
                registrationNumber = "DE-SAME",
                operatingCountry = "DE",
                primaryBusinessAddress = "Same Street 1"
            ),
            primaryContact = PrimaryContact(
                fullName = "Contact",
                email = "same@example.com",
                phoneNumber = "+490000"
            )
        )
        whenever(repository.findById(application.id)).thenReturn(Optional.of(application))
        whenever(repository.save(any<MerchantApplication>())).thenAnswer { it.getArgument<MerchantApplication>(0) }

        service.update(
            application.id,
            UpdateMerchantRequest(
                legalBusinessName = "Same Name GmbH",
                primaryBusinessAddress = "Same Street 1",
                contactFullName = "Contact",
                contactEmail = "same@example.com",
                contactPhoneNumber = "+490000",
                activePaymentChannels = emptySet()
            )
        )

        verify(eventPublisher, never()).publishEvent(any<MerchantNameUpdated>())
    }

    @Test
    fun `deleteMerchantApplication deletes branches before the application`() {
        val applicationId = UUID.randomUUID()
        val application = MerchantApplication(
            applicationReference = "BLTZ-DEL",
            businessProfile = BusinessProfile(
                legalBusinessName = "To Delete GmbH",
                businessType = "LLC",
                registrationNumber = "DE-999",
                operatingCountry = "DE",
                primaryBusinessAddress = "Delete Street 1"
            ),
            primaryContact = PrimaryContact(
                fullName = "Delete Me",
                email = "delete@example.com",
                phoneNumber = "+49000000000"
            )
        )
        whenever(repository.findById(applicationId)).thenReturn(Optional.of(application))

        service.deleteMerchantApplication(applicationId)

        inOrder(branchRepository, repository) {
            verify(branchRepository).deleteAllByMerchantApplicationId(applicationId)
            verify(repository).delete(application)
        }
    }

    @Test
    fun `deleteMerchantApplication deletes monitoring record before the application`() {
        val applicationId = UUID.randomUUID()
        val monitoringRecord = MonitoringRecord(lastTriggerReason = "test")
        val application = MerchantApplication(
            applicationReference = "BLTZ-MON",
            businessProfile = BusinessProfile(
                legalBusinessName = "Monitored GmbH",
                businessType = "LLC",
                registrationNumber = "DE-888",
                operatingCountry = "DE",
                primaryBusinessAddress = "Monitor Street 1"
            ),
            primaryContact = PrimaryContact(
                fullName = "Monitored",
                email = "mon@example.com",
                phoneNumber = "+49111111111"
            )
        ).also { it.monitoringRecord = monitoringRecord }
        whenever(repository.findById(applicationId)).thenReturn(Optional.of(application))

        service.deleteMerchantApplication(applicationId)

        inOrder(monitoringRecordRepository, repository) {
            verify(monitoringRecordRepository).deleteById(monitoringRecord.id)
            verify(repository).delete(application)
        }
    }
}
