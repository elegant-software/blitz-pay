package com.elegant.software.blitzpay.betterprice.agent.api

import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ComparableOffer
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ExplanationCode
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MatchConfidence
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MatchedProduct
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringBottleneck
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringFailure
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringFailureCode
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringStage
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringWarning
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringWarningCode
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.OfferQualificationStatus
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.PriceComparisonResult
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.PriceComparisonStatus
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ProductLookupRequest
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.RequestMonitoringSnapshot
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Structured KOOG product price-research request")
data class PriceComparisonChatRequest(
    val inputPrice: BigDecimal,
    val currency: String,
    val productTitle: String? = null,
    val brandName: String? = null,
    val modelName: String? = null,
    val sku: String? = null,
    val additionalAttributes: Map<String, String> = emptyMap()
)

@Schema(description = "Structured KOOG-backed price-comparison result with provider-neutral active-request monitoring")
data class PriceComparisonResultDto(
    val status: PriceComparisonStatus,
    val inputPrice: BigDecimal,
    val currency: String,
    val matchedProduct: MatchedProductDto? = null,
    val bestOffer: ComparableOfferDto? = null,
    val savingsAmount: BigDecimal,
    val savingsPercentage: BigDecimal? = null,
    val consideredOfferCount: Int,
    val excludedOfferCount: Int,
    val explanationCode: ExplanationCode,
    val monitoring: RequestMonitoringSnapshotDto
)

data class MatchedProductDto(
    val normalizedTitle: String,
    val brandName: String? = null,
    val modelName: String? = null,
    val sku: String? = null,
    val matchConfidence: MatchConfidence,
    val matchEvidence: List<String>
)

data class ComparableOfferDto(
    val sellerName: String,
    val offerPrice: BigDecimal,
    val currency: String,
    val productUrl: String? = null,
    val availability: Boolean,
    val qualificationStatus: OfferQualificationStatus,
    val qualificationNotes: String
)

data class RequestMonitoringSnapshotDto(
    val stage: MonitoringStage,
    val progress: Int,
    val warnings: List<MonitoringWarningDto> = emptyList(),
    val bottleneck: MonitoringBottleneckDto? = null,
    val failure: MonitoringFailureDto? = null
)

data class MonitoringWarningDto(
    val code: MonitoringWarningCode,
    val detail: String
)

data class MonitoringBottleneckDto(
    val stage: MonitoringStage,
    val reason: String,
    val detail: String? = null
)

data class MonitoringFailureDto(
    val code: MonitoringFailureCode,
    val message: String,
    val retriable: Boolean = false
)

fun PriceComparisonChatRequest.toDomain(): ProductLookupRequest = ProductLookupRequest(
    inputPrice = inputPrice,
    currency = currency,
    productTitle = productTitle,
    brandName = brandName,
    modelName = modelName,
    sku = sku,
    additionalAttributes = additionalAttributes
)

fun PriceComparisonResult.toDto(): PriceComparisonResultDto = PriceComparisonResultDto(
    status = status,
    inputPrice = inputPrice,
    currency = currency,
    matchedProduct = matchedProduct?.toDto(),
    bestOffer = bestOffer?.toDto(),
    savingsAmount = savingsAmount,
    savingsPercentage = savingsPercentage,
    consideredOfferCount = consideredOfferCount,
    excludedOfferCount = excludedOfferCount,
    explanationCode = explanationCode,
    monitoring = monitoring.toDto()
)

private fun MatchedProduct.toDto(): MatchedProductDto = MatchedProductDto(
    normalizedTitle = normalizedTitle,
    brandName = brandName,
    modelName = modelName,
    sku = sku,
    matchConfidence = matchConfidence,
    matchEvidence = matchEvidence
)

private fun ComparableOffer.toDto(): ComparableOfferDto = ComparableOfferDto(
    sellerName = sellerName,
    offerPrice = offerPrice,
    currency = currency,
    productUrl = productUrl,
    availability = availability,
    qualificationStatus = qualificationStatus,
    qualificationNotes = qualificationNotes
)

private fun RequestMonitoringSnapshot.toDto(): RequestMonitoringSnapshotDto = RequestMonitoringSnapshotDto(
    stage = stage,
    progress = progress,
    warnings = warnings.map(MonitoringWarning::toDto),
    bottleneck = bottleneck?.toDto(),
    failure = failure?.toDto()
)

private fun MonitoringWarning.toDto(): MonitoringWarningDto = MonitoringWarningDto(
    code = code,
    detail = detail
)

private fun MonitoringBottleneck.toDto(): MonitoringBottleneckDto = MonitoringBottleneckDto(
    stage = stage,
    reason = reason,
    detail = detail
)

private fun MonitoringFailure.toDto(): MonitoringFailureDto = MonitoringFailureDto(
    code = code,
    message = message,
    retriable = retriable
)
