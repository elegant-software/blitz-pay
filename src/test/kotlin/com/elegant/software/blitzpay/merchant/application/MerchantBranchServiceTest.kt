package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.CreateBranchRequest
import com.elegant.software.blitzpay.merchant.api.UpdateBranchRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantBranch
import com.elegant.software.blitzpay.merchant.domain.MerchantPaymentChannel
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class MerchantBranchServiceTest {
    private val branchRepository = mock<MerchantBranchRepository>()
    private val merchantRepository = mock<MerchantApplicationRepository>()
    private val storageService = mock<StorageService>()
    private val service = MerchantBranchService(branchRepository, merchantRepository, storageService)

    @Test
    fun `create branch stores operational address and geolocation`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(branchRepository.save(any<MerchantBranch>())).thenAnswer { it.arguments[0] }

        val response = service.create(
            merchantId,
            CreateBranchRequest(
                name = "Bremen Mitte",
                addressLine1 = "Marktplatz 1",
                city = "Bremen",
                postalCode = "28195",
                country = "DE",
                activePaymentChannels = setOf(MerchantPaymentChannel.TRUELAYER, MerchantPaymentChannel.STRIPE),
                latitude = 53.0758,
                longitude = 8.8072,
                geofenceRadiusMeters = 250,
                googlePlaceId = "ChIJ-test"
            )
        )

        assertEquals("Marktplatz 1", response.addressLine1)
        assertEquals("Bremen", response.city)
        assertEquals(53.0758, response.latitude)
        assertEquals(8.8072, response.longitude)
        assertEquals(250, response.geofenceRadiusMeters)
        assertEquals("ChIJ-test", response.googlePlaceId)
        assertEquals("PENDING", response.placeEnrichmentStatus)
        assertEquals(setOf(MerchantPaymentChannel.TRUELAYER, MerchantPaymentChannel.STRIPE), response.activePaymentChannels)

        val branchCaptor = argumentCaptor<MerchantBranch>()
        verify(branchRepository).save(branchCaptor.capture())
        assertEquals(
            setOf(MerchantPaymentChannel.TRUELAYER, MerchantPaymentChannel.STRIPE),
            branchCaptor.firstValue.activePaymentChannels.toSet()
        )
    }

    @Test
    fun `update branch stores contact and activation state`() {
        val merchantId = UUID.randomUUID()
        val branch = MerchantBranch(
            merchantApplicationId = merchantId,
            name = "Bremen Mitte"
        )
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(branchRepository.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(branchRepository.save(any<MerchantBranch>())).thenAnswer { it.arguments[0] }

        val response = service.update(
            merchantId,
            branch.id,
            UpdateBranchRequest(
                name = "Bremen Nord",
                active = false,
                addressLine1 = "Neue Strasse 1",
                city = "Bremen",
                postalCode = "28195",
                country = "DE",
                contactFullName = "Branch Lead",
                contactEmail = "branch@example.com",
                contactPhoneNumber = "+4930123456",
                activePaymentChannels = setOf(MerchantPaymentChannel.PAYPAL, MerchantPaymentChannel.TRUELAYER)
            )
        )

        assertEquals("Bremen Nord", response.name)
        assertEquals(false, response.active)
        assertEquals("Branch Lead", response.contactFullName)
        assertEquals("branch@example.com", response.contactEmail)
        assertEquals("+4930123456", response.contactPhoneNumber)
        assertEquals(setOf(MerchantPaymentChannel.PAYPAL, MerchantPaymentChannel.TRUELAYER), response.activePaymentChannels)

        val branchCaptor = argumentCaptor<MerchantBranch>()
        verify(branchRepository).save(branchCaptor.capture())
        assertEquals(
            setOf(MerchantPaymentChannel.PAYPAL, MerchantPaymentChannel.TRUELAYER),
            branchCaptor.firstValue.activePaymentChannels.toSet()
        )
    }
}
