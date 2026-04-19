package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductListResponse
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantProduct
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class MerchantProductService(
    private val productRepository: MerchantProductRepository,
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val entityManager: EntityManager
) {
    private val log = LoggerFactory.getLogger(MerchantProductService::class.java)

    fun create(merchantId: UUID, request: CreateProductRequest): ProductResponse {
        requireMerchantExists(merchantId)
        require(request.name.isNotBlank()) { "Product name must not be blank" }
        require(request.unitPrice >= java.math.BigDecimal.ZERO) { "unitPrice must be >= 0" }

        val product = MerchantProduct(
            merchantApplicationId = merchantId,
            name = request.name.trim(),
            unitPrice = request.unitPrice,
            imageUrl = request.imageUrl
        )
        val saved = productRepository.save(product)
        log.info("Product created: id={} merchant={}", saved.id, merchantId)
        return saved.toResponse(merchantId)
    }

    @Transactional(readOnly = true)
    fun list(merchantId: UUID): ProductListResponse {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val products = productRepository.findAllByActiveTrue().map { it.toResponse(merchantId) }
        return ProductListResponse(merchantId = merchantId, products = products)
    }

    @Transactional(readOnly = true)
    fun get(merchantId: UUID, productId: UUID): ProductResponse {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val product = productRepository.findByIdAndActiveTrue(productId)
            .orElseThrow { NoSuchElementException("Product not found: $productId") }
        return product.toResponse(merchantId)
    }

    fun update(merchantId: UUID, productId: UUID, request: UpdateProductRequest): ProductResponse {
        requireMerchantExists(merchantId)
        require(request.name.isNotBlank()) { "Product name must not be blank" }
        require(request.unitPrice >= java.math.BigDecimal.ZERO) { "unitPrice must be >= 0" }

        enableTenantFilter(merchantId)
        val product = productRepository.findByIdAndActiveTrue(productId)
            .orElseThrow { NoSuchElementException("Product not found: $productId") }

        product.update(request.name.trim(), request.unitPrice, request.imageUrl)
        val saved = productRepository.save(product)
        log.info("Product updated: id={} merchant={}", productId, merchantId)
        return saved.toResponse(merchantId)
    }

    fun deactivate(merchantId: UUID, productId: UUID) {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val product = productRepository.findByIdAndActiveTrue(productId)
            .orElseThrow { NoSuchElementException("Product not found or already inactive: $productId") }

        product.deactivate()
        productRepository.save(product)
        log.info("Product deactivated: id={} merchant={}", productId, merchantId)
    }

    private fun requireMerchantExists(merchantId: UUID) {
        require(merchantApplicationRepository.existsById(merchantId)) {
            "Merchant not found: $merchantId"
        }
    }

    private fun enableTenantFilter(merchantId: UUID) {
        val session = entityManager.unwrap(Session::class.java)
        session.enableFilter("tenantFilter").setParameter("merchantId", merchantId)
        entityManager.createNativeQuery("SET LOCAL app.current_merchant_id = :mid")
            .setParameter("mid", merchantId.toString())
            .executeUpdate()
    }

    private fun MerchantProduct.toResponse(merchantId: UUID) = ProductResponse(
        productId = id,
        merchantId = merchantId,
        name = name,
        unitPrice = unitPrice,
        imageUrl = imageUrl,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
