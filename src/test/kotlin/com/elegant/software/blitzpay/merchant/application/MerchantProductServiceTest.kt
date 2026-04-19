package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantProduct
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.hibernate.Filter
import org.hibernate.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
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
    private val entityManager = mock<EntityManager>()
    private val session = mock<Session>()
    private val hibernateFilter = mock<Filter>()
    private val nativeQuery = mock<Query>()

    private val service = MerchantProductService(productRepository, merchantApplicationRepository, entityManager)
    private val merchantId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter(any())).thenReturn(hibernateFilter)
        whenever(hibernateFilter.setParameter(any<String>(), any())).thenReturn(hibernateFilter)
        whenever(entityManager.createNativeQuery(any<String>())).thenReturn(nativeQuery)
        whenever(nativeQuery.setParameter(any<String>(), any())).thenReturn(nativeQuery)
        whenever(nativeQuery.executeUpdate()).thenReturn(0)
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
    }

    @Test
    fun `create saves product with correct fields`() {
        val request = CreateProductRequest(name = "Coffee Blend", unitPrice = BigDecimal("12.50"))
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request)

        assertEquals("Coffee Blend", response.name)
        assertEquals(BigDecimal("12.50"), response.unitPrice)
        assertEquals(merchantId, response.merchantId)
        assertTrue(response.active)
    }

    @Test
    fun `create allows zero-price products`() {
        val request = CreateProductRequest(name = "Free Sample", unitPrice = BigDecimal.ZERO)
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request)

        assertEquals(BigDecimal.ZERO, response.unitPrice)
    }

    @Test
    fun `create rejects negative price`() {
        val request = CreateProductRequest(name = "Invalid", unitPrice = BigDecimal("-1.00"))

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `create rejects blank product name`() {
        val request = CreateProductRequest(name = "   ", unitPrice = BigDecimal("5.00"))

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `create fails when merchant does not exist`() {
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(false)
        val request = CreateProductRequest(name = "Coffee", unitPrice = BigDecimal("10.00"))

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `get throws when product not found`() {
        val productId = UUID.randomUUID()
        whenever(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.empty())

        assertFailsWith<NoSuchElementException> {
            service.get(merchantId, productId)
        }
    }

    @Test
    fun `deactivate throws when product already inactive`() {
        val productId = UUID.randomUUID()
        whenever(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.empty())

        assertFailsWith<NoSuchElementException> {
            service.deactivate(merchantId, productId)
        }
    }
}
