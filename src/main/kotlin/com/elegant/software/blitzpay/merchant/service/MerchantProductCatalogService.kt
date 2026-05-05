package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.CatalogProduct
import com.elegant.software.blitzpay.merchant.api.MerchantProductCatalogGateway
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MerchantProductCatalogService(
    private val productRepository: MerchantProductRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
    private val storageService: StorageService,
) : MerchantProductCatalogGateway {

    override fun findActiveProducts(merchantId: UUID, branchId: UUID): List<CatalogProduct> {
        if (!merchantBranchRepository.existsByMerchantApplicationIdAndIdAndActiveTrue(merchantId, branchId)) {
            return emptyList()
        }

        return productRepository.findAllByActiveTrueAndMerchantBranchId(branchId)
            .asSequence()
            .filter { it.merchantApplicationId == merchantId }
            .map { product ->
                CatalogProduct(
                    productId = product.id,
                    branchId = branchId,
                    name = product.name,
                    description = product.description,
                    unitPrice = product.unitPrice,
                    imageUrl = product.imageStorageKey?.let(storageService::presignDownload),
                )
            }
            .toList()
    }
}
