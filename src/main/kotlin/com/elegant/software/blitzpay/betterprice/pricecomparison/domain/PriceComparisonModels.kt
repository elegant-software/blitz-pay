package com.elegant.software.blitzpay.betterprice.pricecomparison.domain

import java.math.BigDecimal

data class ProductLookupRequest(
    val inputPrice: BigDecimal,
    val currency: String,
    val productTitle: String? = null,
    val brandName: String? = null,
    val modelName: String? = null,
    val sku: String? = null,
    val additionalAttributes: Map<String, String> = emptyMap()
)

data class MatchedProduct(
    val normalizedTitle: String,
    val brandName: String? = null,
    val modelName: String? = null,
    val sku: String? = null,
    val matchConfidence: MatchConfidence,
    val matchEvidence: List<String>
)

data class ComparableOffer(
    val sellerName: String,
    val offerPrice: BigDecimal,
    val currency: String,
    val productUrl: String? = null,
    val availability: Boolean,
    val qualificationStatus: OfferQualificationStatus,
    val qualificationNotes: String
)

data class RequestMonitoringSnapshot(
    val stage: MonitoringStage,
    val progress: Int,
    val warnings: List<MonitoringWarning> = emptyList(),
    val bottleneck: MonitoringBottleneck? = null,
    val failure: MonitoringFailure? = null
)

data class MonitoringWarning(
    val code: MonitoringWarningCode,
    val detail: String
)

data class MonitoringBottleneck(
    val stage: MonitoringStage,
    val reason: String,
    val detail: String? = null
)

data class MonitoringFailure(
    val code: MonitoringFailureCode,
    val message: String,
    val retriable: Boolean = false
)

data class PriceComparisonResult(
    val status: PriceComparisonStatus,
    val inputPrice: BigDecimal,
    val currency: String,
    val matchedProduct: MatchedProduct? = null,
    val bestOffer: ComparableOffer? = null,
    val savingsAmount: BigDecimal,
    val savingsPercentage: BigDecimal? = null,
    val consideredOfferCount: Int,
    val excludedOfferCount: Int,
    val explanationCode: ExplanationCode,
    val monitoring: RequestMonitoringSnapshot
)
