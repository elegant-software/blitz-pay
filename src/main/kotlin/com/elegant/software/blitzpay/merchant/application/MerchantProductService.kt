package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantProduct
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.storage.StorageService
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
@Transactional
class MerchantProductService(
    private val productRepository: MerchantProductRepository,
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
    private val entityManager: EntityManager,
    private val storageService: StorageService
) {
    private val log = LoggerFactory.getLogger(MerchantProductService::class.java)

    fun create(merchantId: UUID, request: CreateProductRequest, image: ProductImageUpload? = null): ProductResponse {
        requireMerchantExists(merchantId)
        validateProductFields(request.name, request.description, request.unitPrice)
        val productId = UUID.randomUUID()
        val imageStorageKey = image?.let { uploadImage(merchantId, productId, it, request.branchId) }

        require(merchantBranchRepository.existsByMerchantApplicationIdAndIdAndActiveTrue(merchantId, request.branchId)) {
            "Merchant branch not found or does not belong to merchant: ${request.branchId}"
        }

        val product = MerchantProduct(
            id = productId,
            merchantApplicationId = merchantId,
            name = request.name.trim(),
            description = ProductImagePolicy.normalizeDescription(request.description),
            unitPrice = request.unitPrice,
            imageStorageKey = imageStorageKey,
            merchantBranchId = request.branchId
        )
        val saved = try {
            productRepository.save(product)
        } catch (ex: RuntimeException) {
            imageStorageKey?.let { cleanupUploadedImage(it) }
            throw ex
        }
        log.info("Product created: id={} merchant={}", saved.id, merchantId)
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(merchantId: UUID, merchantBranchId: UUID): List<ProductResponse> {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val products = productRepository.findAllByActiveTrueAndMerchantBranchId(merchantBranchId)
            .map { it.toResponse() }
        return products
    }

    @Transactional(readOnly = true)
    fun get(merchantId: UUID, productId: UUID, merchantBranchId: UUID): ProductResponse {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val product = productRepository.findByIdAndActiveTrueAndMerchantBranchId(productId, merchantBranchId)
            .orElseThrow { NoSuchElementException("Product not found: $productId") }
        return product.toResponse()
    }

    fun update(merchantId: UUID, productId: UUID, request: UpdateProductRequest, image: ProductImageUpload? = null): ProductResponse {
        requireMerchantExists(merchantId)
        validateProductFields(request.name, request.description, request.unitPrice)

        enableTenantFilter(merchantId)
        val product = productRepository.findByIdAndActiveTrue(productId)
            .orElseThrow { NoSuchElementException("Product not found: $productId") }

        val previousImageStorageKey = product.imageStorageKey
        val newImageStorageKey = image?.let { uploadImage(merchantId, productId, it, request.branchId) } ?: previousImageStorageKey
        product.update(
            name = request.name.trim(),
            description = ProductImagePolicy.normalizeDescription(request.description),
            unitPrice = request.unitPrice,
            imageStorageKey = newImageStorageKey
        )
        // handle branch reassignment if provided
        val newBranchId = request.branchId
        if (newBranchId != product.merchantBranchId) {
            require(merchantBranchRepository.existsByMerchantApplicationIdAndIdAndActiveTrue(merchantId, newBranchId)) {
                "Merchant branch not found or does not belong to merchant: $newBranchId"
            }
            product.merchantBranchId = newBranchId
        }
        val saved = try {
            productRepository.save(product)
        } catch (ex: RuntimeException) {
            if (newImageStorageKey != null && newImageStorageKey != previousImageStorageKey) {
                cleanupUploadedImage(newImageStorageKey)
            }
            throw ex
        }
        log.info("Product updated: id={} merchant={}", productId, merchantId)
        return saved.toResponse()
    }

    fun deactivate(merchantId: UUID, productId: UUID, merchantBranchId: UUID) {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val product = productRepository.findByIdAndActiveTrueAndMerchantBranchId(productId, merchantBranchId)
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
        entityManager.createNativeQuery("SELECT set_config('app.current_merchant_id', :mid, true)")
            .setParameter("mid", merchantId.toString())
            .singleResult
    }

    private fun validateProductFields(name: String, description: String?, unitPrice: BigDecimal) {
        require(name.isNotBlank()) { "Product name must not be blank" }
        ProductImagePolicy.normalizeDescription(description)
        require(unitPrice >= BigDecimal.ZERO) { "unitPrice must be >= 0" }
    }

    private fun uploadImage(merchantId: UUID, productId: UUID, image: ProductImageUpload, merchantBranchId: UUID): String {
        val storageKey = "merchants/$merchantId/branches/$merchantBranchId/products/$productId/image.${image.extension}"
        storageService.upload(storageKey, image.contentType, image.bytes)
        return storageKey
    }

    private fun cleanupUploadedImage(storageKey: String) {
        runCatching { storageService.delete(storageKey) }
            .onFailure { log.warn("Failed to clean up uploaded product image key={}", storageKey, it) }
    }

    private fun signedImageUrl(storageKey: String?): String? =
        storageKey?.let {
            runCatching { storageService.presignDownload(it) }
                .onFailure { ex -> log.warn("Failed to sign product image key={}", it, ex) }
                .getOrNull()
        }

    private fun MerchantProduct.toResponse() = ProductResponse(
        productId = id,
        branchId = merchantBranchId!!,
        name = name,
        description = description,
        unitPrice = unitPrice,
        imageUrl = signedImageUrl(imageStorageKey),
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
