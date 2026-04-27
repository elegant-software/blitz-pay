package com.elegant.software.blitzpay.voice.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigDecimal
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = AssistantResponse.Transcript::class, name = "TRANSCRIPT"),
    JsonSubTypes.Type(value = AssistantResponse.ProductResult::class, name = "PRODUCT_RESULT"),
    JsonSubTypes.Type(value = AssistantResponse.NoMatch::class, name = "NO_MATCH"),
)
sealed class AssistantResponse {

    data class Transcript(
        val transcript: String,
        val language: String? = null,
    ) : AssistantResponse()

    data class ProductResult(
        val products: List<ProductMatch>,
        val requestedQuantity: Int? = null,
    ) : AssistantResponse()

    data class NoMatch(
        val message: String,
    ) : AssistantResponse()
}

data class ProductMatch(
    val productId: UUID,
    val branchId: UUID,
    val name: String,
    val description: String?,
    val unitPrice: BigDecimal,
    val imageUrl: String?,
)
