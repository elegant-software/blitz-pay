package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.BranchResponse
import com.elegant.software.blitzpay.merchant.api.CreateBranchRequest
import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductCategoryResponse
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.application.MerchantBranchService
import com.elegant.software.blitzpay.merchant.service.MerchantProductCategoryService
import com.elegant.software.blitzpay.merchant.service.MerchantProductService
import com.elegant.software.blitzpay.merchant.application.MerchantRegistrationService
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
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
    private val merchantProductCategoryService = mock<MerchantProductCategoryService>()

    private val tools = MerchantProductTools(
        merchantProductService = merchantProductService,
        merchantBranchService = merchantBranchService,
        merchantRegistrationService = merchantRegistrationService,
        merchantProductCategoryService = merchantProductCategoryService
    )

    @Test
    fun `getOrCreateMerchantId returns existing merchant and does not register when found`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantRegistrationService.findByName("Cafe Blue"))
            .thenReturn(merchant(merchantId, "Cafe Blue", "REG-001"))
        whenever(merchantBranchService.findByNameIncludingInactive(merchantId, "Main Branch")).thenReturn(null)
        whenever(merchantBranchService.create(eq(merchantId), any<CreateBranchRequest>(), eq(false))).thenReturn(branchResponse(merchantId))

        val result = tools.getOrCreateMerchantId(merchantName = "Cafe Blue")

        assertEquals(merchantId.toString(), result)
        verify(merchantRegistrationService, never()).registerDraft(any())
        verify(merchantBranchService).create(eq(merchantId), any<CreateBranchRequest>(), eq(false))
    }

    @Test
    fun `getOrCreateMerchantId creates merchant and default branch when merchant is missing`() {
        whenever(merchantRegistrationService.findByName("Fresh Mart")).thenReturn(null)

        val merchantId = UUID.randomUUID()
        whenever(merchantRegistrationService.registerDraft(any())).thenReturn(merchant(merchantId, "Fresh Mart", "REG-NEW"))
        whenever(merchantBranchService.findByNameIncludingInactive(merchantId, "Main Branch")).thenReturn(null)
        whenever(merchantBranchService.create(eq(merchantId), any<CreateBranchRequest>(), eq(false))).thenReturn(branchResponse(merchantId))

        val result = tools.getOrCreateMerchantId(merchantName = "Fresh Mart")

        assertEquals(merchantId.toString(), result)
        val registerCaptor = argumentCaptor<com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest>()
        verify(merchantRegistrationService).registerDraft(registerCaptor.capture())
        assertEquals("Fresh Mart", registerCaptor.firstValue.businessProfile.legalBusinessName)
        assertEquals("Main Branch", captureBranchName())
    }

    @Test
    fun `getOrCreateProductId accepts empty image inputs and creates product without image`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, "Latte")).thenReturn(null)
        whenever(merchantProductService.create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false))).thenReturn(
            ProductResponse(
                productId = UUID.randomUUID(),
                branchId = branchId,
                name = "Latte",
                description = null,
                unitPrice = BigDecimal("3.50"),
                imageUrl = null,
                active = false,
                status = "INACTIVE",
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

        verify(merchantProductService).create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false))
    }

    @Test
    fun `getCategoryIdByName returns id when found`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductCategoryService.findByName(merchantId, "Drinks")).thenReturn(
            ProductCategoryResponse(categoryId, "Drinks", Instant.now(), Instant.now())
        )

        val result = tools.getCategoryIdByName(merchantId.toString(), "Drinks")

        assertEquals(categoryId.toString(), result)
    }

    @Test
    fun `getCategoryIdByName throws when not found`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantProductCategoryService.findByName(merchantId, "Missing")).thenReturn(null)

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            tools.getCategoryIdByName(merchantId.toString(), "Missing")
        }
    }

    @Test
    fun `getOrCreateCategoryId returns existing id`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductCategoryService.findByName(merchantId, "Drinks")).thenReturn(
            ProductCategoryResponse(categoryId, "Drinks", Instant.now(), Instant.now())
        )

        val result = tools.getOrCreateCategoryId(merchantId.toString(), "Drinks")

        assertEquals(categoryId.toString(), result)
    }

    @Test
    fun `getOrCreateCategoryId creates category when absent`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductCategoryService.findByName(merchantId, "Wine")).thenReturn(null)
        whenever(
            merchantProductCategoryService.create(eq(merchantId), any<CreateProductCategoryRequest>())
        ).thenReturn(ProductCategoryResponse(categoryId, "Wine", Instant.now(), Instant.now()))

        val result = tools.getOrCreateCategoryId(merchantId.toString(), "Wine")

        assertEquals(categoryId.toString(), result)
        verify(merchantProductCategoryService).create(eq(merchantId), any<CreateProductCategoryRequest>())
    }

    @Test
    fun `listProductCategories delegates to service`() {
        val merchantId = UUID.randomUUID()
        val categories = listOf(ProductCategoryResponse(UUID.randomUUID(), "Snacks", Instant.now(), Instant.now()))
        whenever(merchantProductCategoryService.list(merchantId)).thenReturn(categories)

        val result = tools.listProductCategories(merchantId.toString())

        assertEquals(categories, result)
    }

    @Test
    fun `updateProduct passes categoryId through to request`() {
        val merchantId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductService.updateIncludingInactive(eq(merchantId), eq(productId), any(), isNull())).thenReturn(
            ProductResponse(
                productId = productId,
                branchId = branchId,
                name = "Latte",
                description = null,
                unitPrice = BigDecimal("3.50"),
                imageUrl = null,
                active = true,
                status = "ACTIVE",
                categoryId = categoryId,
                categoryName = "Drinks",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        tools.updateProduct(
            merchantId = merchantId.toString(),
            productId = productId.toString(),
            branchId = branchId.toString(),
            name = "Latte",
            unitPrice = "3.50",
            categoryId = categoryId.toString(),
            productCode = "12"
        )

        val captor = argumentCaptor<com.elegant.software.blitzpay.merchant.api.UpdateProductRequest>()
        verify(merchantProductService).updateIncludingInactive(eq(merchantId), eq(productId), captor.capture(), isNull())
        assertEquals(categoryId, captor.firstValue.categoryId)
        assertEquals(12L, captor.firstValue.productCode)
    }

    @Test
    fun `updateProduct uses inactive aware service path`() {
        val merchantId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantProductService.updateIncludingInactive(eq(merchantId), eq(productId), any(), isNull())).thenReturn(
            ProductResponse(
                productId = productId,
                branchId = branchId,
                name = "Latte",
                description = null,
                unitPrice = BigDecimal("3.50"),
                imageUrl = null,
                active = true,
                status = "ACTIVE",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        tools.updateProduct(
            merchantId = merchantId.toString(),
            productId = productId.toString(),
            branchId = branchId.toString(),
            name = "Latte",
            unitPrice = "3.50"
        )

        verify(merchantProductService).updateIncludingInactive(eq(merchantId), eq(productId), any(), isNull())
        verify(merchantProductService, never()).update(eq(merchantId), eq(productId), any(), isNull())
        verify(merchantProductService, never()).markInactive(any(), any())
    }

    @Test
    fun `getOrCreateProductId passes product code into create request`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, "Latte")).thenReturn(null)
        whenever(merchantProductService.create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false))).thenReturn(
            ProductResponse(
                productId = UUID.randomUUID(),
                branchId = branchId,
                name = "Latte",
                description = null,
                unitPrice = BigDecimal("3.50"),
                imageUrl = null,
                active = false,
                status = "INACTIVE",
                productCode = 12L,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        tools.getOrCreateProductId(
            merchantId = merchantId.toString(),
            branchId = branchId.toString(),
            productName = "Latte",
            unitPrice = "3.50",
            productCode = "12"
        )

        val captor = argumentCaptor<CreateProductRequest>()
        verify(merchantProductService).create(eq(merchantId), captor.capture(), isNull(), eq(false))
        assertEquals(12L, captor.firstValue.productCode)
    }

    private fun captureBranchName(): String {
        val requestCaptor = argumentCaptor<CreateBranchRequest>()
        verify(merchantBranchService).create(any(), requestCaptor.capture(), eq(false))
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
            ),
            status = MerchantOnboardingStatus.DRAFT
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
            status = "INACTIVE",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
}
