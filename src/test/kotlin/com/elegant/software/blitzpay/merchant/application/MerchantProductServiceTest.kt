package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantProduct
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import com.elegant.software.blitzpay.storage.StorageService
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.hibernate.Filter
import org.hibernate.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MerchantProductServiceTest {

    private val productRepository = mock<MerchantProductRepository>()
    private val merchantApplicationRepository = mock<MerchantApplicationRepository>()
    private val merchantBranchRepository = mock<com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository>()
    private val entityManager = mock<EntityManager>()
    private val storageService = mock<StorageService>()
    private val session = mock<Session>()
    private val hibernateFilter = mock<Filter>()
    private val nativeQuery = mock<Query>()

    private val service = MerchantProductService(
        productRepository,
        merchantApplicationRepository,
        merchantBranchRepository,
        entityManager,
        storageService
    )
    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter(any())).thenReturn(hibernateFilter)
        whenever(hibernateFilter.setParameter(any<String>(), any())).thenReturn(hibernateFilter)
        whenever(entityManager.createNativeQuery(any<String>())).thenReturn(nativeQuery)
        whenever(nativeQuery.setParameter(any<String>(), any())).thenReturn(nativeQuery)
        whenever(nativeQuery.singleResult).thenReturn(merchantId.toString())
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(merchantBranchRepository.existsByMerchantApplicationIdAndIdAndActiveTrue(merchantId, branchId)).thenReturn(true)
        whenever(storageService.presignDownload(any(), any())).thenAnswer { "https://signed.example/${it.arguments[0]}" }
    }

    @Test
    fun `create saves product with correct fields`() {
        val request = CreateProductRequest(
            name = "Coffee Blend",
            branchId = branchId,
            description = "**Medium roast**",
            unitPrice = BigDecimal("12.50")
        )
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request)

        assertEquals("Coffee Blend", response.name)
        assertEquals("**Medium roast**", response.description)
        assertEquals(BigDecimal("12.50"), response.unitPrice)
        assertTrue(response.active)
    }

    @Test
    fun `create allows zero-price products`() {
        val request = CreateProductRequest(name = "Free Sample", branchId = branchId, unitPrice = BigDecimal.ZERO)
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request)

        assertEquals(BigDecimal.ZERO, response.unitPrice)
    }

    @Test
    fun `create rejects negative price`() {
        val request = CreateProductRequest(name = "Invalid", branchId = branchId, unitPrice = BigDecimal("-1.00"))

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `create rejects blank product name`() {
        val request = CreateProductRequest(name = "   ", branchId = branchId, unitPrice = BigDecimal("5.00"))

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `create rejects overlong description`() {
        val request = CreateProductRequest(
            name = "Coffee",
            branchId = branchId,
            description = "x".repeat(2_001),
            unitPrice = BigDecimal("10.00")
        )

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `create uploads valid image and stores object key`() {
        val request = CreateProductRequest(name = "Coffee", branchId = branchId, unitPrice = BigDecimal("10.00"))
        val upload = ProductImageUpload(contentType = "image/webp", bytes = byteArrayOf(1, 2, 3))
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request, upload)

        verify(storageService).upload(
            eq("merchants/$merchantId/branches/$branchId/products/${response.productId}/image.webp"),
            eq("image/webp"),
            eq(byteArrayOf(1, 2, 3))
        )
        assertEquals("https://signed.example/merchants/$merchantId/branches/$branchId/products/${response.productId}/image.webp", response.imageUrl)
    }

    @Test
    fun `update uploads valid image and replaces object key`() {
        val productId = UUID.randomUUID()
        val product = MerchantProduct(
            id = productId,
            merchantApplicationId = merchantId,
            name = "Coffee",
            unitPrice = BigDecimal("10.00"),
            imageStorageKey = "old-key",
            merchantBranchId = branchId
        )
        val request = UpdateProductRequest(
            name = "Coffee Updated",
            branchId = branchId,
            description = "_Updated_",
            unitPrice = BigDecimal("12.00")
        )
        val upload = ProductImageUpload(contentType = "image/png", bytes = byteArrayOf(4, 5, 6))
        whenever(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.of(product))
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.update(merchantId, productId, request, upload)

        verify(storageService).upload(
            eq("merchants/$merchantId/branches/$branchId/products/$productId/image.png"),
            eq("image/png"),
            eq(byteArrayOf(4, 5, 6))
        )
        assertEquals("Coffee Updated", response.name)
        assertEquals("_Updated_", response.description)
        assertEquals("https://signed.example/merchants/$merchantId/branches/$branchId/products/$productId/image.png", response.imageUrl)
    }

    @Test
    fun `invalid image type is rejected before storage upload`() {
        assertFailsWith<IllegalArgumentException> {
            ProductImageUpload(contentType = "text/plain", bytes = byteArrayOf(1))
        }
        verify(storageService, never()).upload(any(), any(), any())
    }

    @Test
    fun `oversized image is rejected before storage upload`() {
        assertFailsWith<IllegalArgumentException> {
            ProductImageUpload(contentType = "image/jpeg", bytes = ByteArray((ProductImagePolicy.MaxBytes + 1).toInt()))
        }
        verify(storageService, never()).upload(any(), any(), any())
    }

    @Test
    fun `storage upload failure does not persist product`() {
        val request = CreateProductRequest(name = "Coffee", branchId = branchId, unitPrice = BigDecimal("10.00"))
        val upload = ProductImageUpload(contentType = "image/png", bytes = byteArrayOf(1))
        whenever(storageService.upload(any(), any(), any())).thenThrow(IllegalStateException("storage down"))

        assertFailsWith<IllegalStateException> {
            service.create(merchantId, request, upload)
        }
        verify(productRepository, never()).save(any<MerchantProduct>())
    }

    @Test
    fun `list returns signed image urls from storage keys`() {
        val product = MerchantProduct(
            merchantApplicationId = merchantId,
            name = "Coffee",
            unitPrice = BigDecimal("10.00"),
            imageStorageKey = "merchants/$merchantId/branches/$branchId/products/product/image.jpg",
            merchantBranchId = branchId
        )
        whenever(productRepository.findAllByActiveTrueAndMerchantBranchId(branchId)).thenReturn(listOf(product))

        val response = service.list(merchantId, branchId)

        assertEquals("https://signed.example/merchants/$merchantId/branches/$branchId/products/product/image.jpg", response.single().imageUrl)
    }

    @Test
    fun `get returns null image url when signing fails`() {
        val productId = UUID.randomUUID()
        val product = MerchantProduct(
            id = productId,
            merchantApplicationId = merchantId,
            name = "Coffee",
            unitPrice = BigDecimal("10.00"),
            imageStorageKey = "missing",
            merchantBranchId = branchId
        )
        whenever(productRepository.findByIdAndActiveTrueAndMerchantBranchId(productId, branchId)).thenReturn(Optional.of(product))
        whenever(storageService.presignDownload(eq("missing"), any())).thenThrow(IllegalStateException("not found"))

        val response = service.get(merchantId, productId, branchId)

        assertEquals(null, response.imageUrl)
    }

    @Test
    fun `create fails when merchant does not exist`() {
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(false)
        val request = CreateProductRequest(name = "Coffee", branchId = branchId, unitPrice = BigDecimal("10.00"))

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `get throws when product not found`() {
        val productId = UUID.randomUUID()
        whenever(productRepository.findByIdAndActiveTrueAndMerchantBranchId(productId, branchId)).thenReturn(Optional.empty())

        assertFailsWith<NoSuchElementException> {
            service.get(merchantId, productId, branchId)
        }
    }

    @Test
    fun `deactivate throws when product already inactive`() {
        val productId = UUID.randomUUID()
        whenever(productRepository.findByIdAndActiveTrueAndMerchantBranchId(productId, branchId)).thenReturn(Optional.empty())

        assertFailsWith<NoSuchElementException> {
            service.deactivate(merchantId, productId, branchId)
        }
    }
}
