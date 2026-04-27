package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.CatalogProduct
import com.elegant.software.blitzpay.merchant.api.MerchantProductCatalogGateway
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MerchantProductCatalogService(
    private val merchantProductService: MerchantProductService,
) : MerchantProductCatalogGateway {

    override fun findActiveProducts(merchantId: UUID, branchId: UUID): List<CatalogProduct> =
        merchantProductService.list(merchantId, branchId).map { product ->
            CatalogProduct(
                productId = product.productId,
                branchId = product.branchId,
                name = product.name,
                description = product.description,
                unitPrice = product.unitPrice,
                imageUrl = product.imageUrl,
            )
        }
}
