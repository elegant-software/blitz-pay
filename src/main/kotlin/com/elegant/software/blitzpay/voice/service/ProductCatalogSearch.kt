package com.elegant.software.blitzpay.voice.service

import com.elegant.software.blitzpay.merchant.api.CatalogProduct
import com.elegant.software.blitzpay.voice.api.AssistantResponse
import com.elegant.software.blitzpay.voice.api.ProductMatch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

interface ProductCatalogSearch {
    fun search(intent: ProductIntent, catalog: List<CatalogProduct>): AssistantResponse
}

@Service
class DefaultProductCatalogSearch : ProductCatalogSearch {
    private val log = LoggerFactory.getLogger(DefaultProductCatalogSearch::class.java)

    override fun search(intent: ProductIntent, catalog: List<CatalogProduct>): AssistantResponse {
        val productsById = catalog.associateBy { it.productId }
        val matches = intent.matchedProductIds
            .mapNotNull(productsById::get)
            .take(5)
            .map { product ->
                ProductMatch(
                    productId = product.productId,
                    branchId = product.branchId,
                    name = product.name,
                    description = product.description,
                    unitPrice = product.unitPrice,
                    imageUrl = product.imageUrl,
                )
            }

        if (matches.isEmpty()) {
            log.info(
                "voice product search produced no matches requestedQuantity={} catalogSize={} matchedProductIds={}",
                intent.requestedQuantity,
                catalog.size,
                intent.matchedProductIds,
            )
            return AssistantResponse.NoMatch(
                "I didn't understand that request — try browsing the product screen."
            )
        }

        log.info(
            "voice product search selected matches requestedQuantity={} matchCount={} matches={}",
            intent.requestedQuantity,
            matches.size,
            matches.map { "${it.productId}:${it.name.take(32)}" }
        )
        return AssistantResponse.ProductResult(
            products = matches,
            requestedQuantity = intent.requestedQuantity,
        )
    }
}
