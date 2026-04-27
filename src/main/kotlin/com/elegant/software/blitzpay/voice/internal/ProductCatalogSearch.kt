package com.elegant.software.blitzpay.voice.internal

import com.elegant.software.blitzpay.merchant.api.CatalogProduct
import com.elegant.software.blitzpay.voice.api.AssistantResponse
import com.elegant.software.blitzpay.voice.api.ProductMatch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ProductCatalogSearch {
    private val log = LoggerFactory.getLogger(ProductCatalogSearch::class.java)

    companion object {
        private const val NO_MATCH_MESSAGE =
            "I didn't understand that request — try browsing the product screen."
    }

    fun search(intent: ProductIntent, catalog: List<CatalogProduct>): AssistantResponse {
        if (intent.matchedProductIds.isEmpty()) {
            return AssistantResponse.NoMatch(message = NO_MATCH_MESSAGE)
        }

        val catalogById: Map<UUID, CatalogProduct> = catalog.associateBy { it.productId }

        val matches = intent.matchedProductIds
            .mapNotNull { id ->
                catalogById[id]?.let { product ->
                    ProductMatch(
                        productId = product.productId,
                        branchId = product.branchId,
                        name = product.name,
                        description = product.description,
                        unitPrice = product.unitPrice,
                        imageUrl = product.imageUrl,
                    )
                }.also { if (it == null) log.debug("Product id={} from intent not found in catalog", id) }
            }

        return if (matches.isEmpty()) {
            AssistantResponse.NoMatch(message = NO_MATCH_MESSAGE)
        } else {
            AssistantResponse.ProductResult(
                products = matches,
                requestedQuantity = intent.requestedQuantity,
            )
        }
    }
}
