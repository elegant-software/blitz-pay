package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.BranchResponse
import com.elegant.software.blitzpay.merchant.api.CreateBranchRequest
import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.application.MerchantBranchService
import com.elegant.software.blitzpay.merchant.application.MerchantProductService
import com.elegant.software.blitzpay.merchant.application.MerchantRegistrationService
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class MerchantProductToolsTest {

    private val merchantProductService = mock<MerchantProductService>()
    private val merchantBranchService = mock<MerchantBranchService>()
    private val merchantRegistrationService = mock<MerchantRegistrationService>()
    private val merchantApplicationRepository = mock<MerchantApplicationRepository>()
    private val merchantBranchRepository = mock<MerchantBranchRepository>()
    private val merchantProductRepository = mock<MerchantProductRepository>()

    private val tools = MerchantProductTools(
        merchantProductService = merchantProductService,
        merchantBranchService = merchantBranchService,
        merchantRegistrationService = merchantRegistrationService,
        merchantApplicationRepository = merchantApplicationRepository,
        merchantBranchRepository = merchantBranchRepository,
        merchantProductRepository = merchantProductRepository
    )

    @Test
    fun `getOrCreateMerchantId returns existing merchant and does not register when found`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantApplicationRepository.findByBusinessProfileLegalBusinessName("Cafe Blue"))
            .thenReturn(merchant(merchantId, "Cafe Blue", "REG-001"))
        whenever(merchantBranchRepository.findByNameAndMerchantApplicationId("Main Branch", merchantId)).thenReturn(null)
        whenever(merchantBranchService.create(eq(merchantId), any<CreateBranchRequest>())).thenReturn(branchResponse(merchantId))

        val result = tools.getOrCreateMerchantId(merchantName = "Cafe Blue")

        assertEquals(merchantId.toString(), result)
        verify(merchantRegistrationService, never()).register(any())
        verify(merchantBranchService).create(eq(merchantId), any<CreateBranchRequest>())
    }

    @Test
    fun `getOrCreateMerchantId creates merchant and default branch when merchant is missing`() {
        whenever(merchantApplicationRepository.findByBusinessProfileLegalBusinessName("Fresh Mart")).thenReturn(null)

        val merchantId = UUID.randomUUID()
        whenever(merchantRegistrationService.register(any())).thenReturn(merchant(merchantId, "Fresh Mart", "REG-NEW"))
        whenever(merchantBranchRepository.findByNameAndMerchantApplicationId("Main Branch", merchantId)).thenReturn(null)
        whenever(merchantBranchService.create(eq(merchantId), any<CreateBranchRequest>())).thenReturn(branchResponse(merchantId))

        val result = tools.getOrCreateMerchantId(merchantName = "Fresh Mart")

        assertEquals(merchantId.toString(), result)
        val registerCaptor = argumentCaptor<com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest>()
        verify(merchantRegistrationService).register(registerCaptor.capture())
        assertEquals("Fresh Mart", registerCaptor.firstValue.businessProfile.legalBusinessName)
        assertEquals("Main Branch", captureBranchName())
    }

    @Test
    fun `getOrCreateProductId accepts empty image inputs and creates product without image`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(
            merchantProductRepository.findByNameAndMerchantApplicationIdAndMerchantBranchId(
                "Latte",
                merchantId,
                branchId
            )
        ).thenReturn(null)
        whenever(merchantProductService.create(eq(merchantId), any<CreateProductRequest>(), isNull())).thenReturn(
            ProductResponse(
                productId = UUID.randomUUID(),
                branchId = branchId,
                name = "Latte",
                description = null,
                unitPrice = BigDecimal("3.50"),
                imageUrl = null,
                active = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        tools.getOrCreateProductId(
            merchantId = merchantId.toString(),
            branchId = branchId.toString(),
            productName = "Latte",
            unitPrice = "3.50",
            imageBase64 = "   ",
            imageFilePath = "",
            imageContentType = "  "
        )

        verify(merchantProductService).create(eq(merchantId), any<CreateProductRequest>(), isNull())
    }

    private fun captureBranchName(): String {
        val requestCaptor = argumentCaptor<CreateBranchRequest>()
        verify(merchantBranchService).create(any(), requestCaptor.capture())
        return requestCaptor.firstValue.name
    }

    private fun merchant(id: UUID, name: String, registrationNumber: String): MerchantApplication =
        MerchantApplication(
            id = id,
            applicationReference = "BLTZ-TEST1234",
            businessProfile = BusinessProfile(
                legalBusinessName = name,
                businessType = "RETAIL",
                registrationNumber = registrationNumber,
                operatingCountry = "US",
                primaryBusinessAddress = "Street 1"
            ),
            primaryContact = PrimaryContact(
                fullName = "Owner",
                email = "owner@example.com",
                phoneNumber = "000"
            )
        )

    private fun branchResponse(merchantId: UUID): BranchResponse =
        BranchResponse(
            id = UUID.randomUUID(),
            merchantId = merchantId,
            name = "Main Branch",
            active = true,
            addressLine1 = null,
            addressLine2 = null,
            city = null,
            postalCode = null,
            country = null,
            contactFullName = null,
            contactEmail = null,
            contactPhoneNumber = null,
            activePaymentChannels = emptySet(),
            latitude = null,
            longitude = null,
            geofenceRadiusMeters = null,
            googlePlaceId = null,
            placeFormattedAddress = null,
            placeRating = null,
            placeReviewCount = null,
            placeEnrichmentStatus = null,
            placeEnrichedAt = null,
            imageUrl = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
}
