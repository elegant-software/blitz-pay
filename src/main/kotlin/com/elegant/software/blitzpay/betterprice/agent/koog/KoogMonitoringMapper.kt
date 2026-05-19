package com.elegant.software.blitzpay.betterprice.agent.koog

import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchBottleneck
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchFailure
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchResult
import com.elegant.software.blitzpay.betterprice.search.domain.MarketSearchWarning
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ExplanationCode
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringBottleneck
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringFailure
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringFailureCode
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringStage
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringWarning
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringWarningCode
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.PriceComparisonResult
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.PriceComparisonStatus
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ProductLookupRequest
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.RequestMonitoringSnapshot
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class KoogMonitoringMapper {

    fun merge(result: PriceComparisonResult, searchPreview: MarketSearchResult?): PriceComparisonResult {
        if (searchPreview == null) {
            return result
        }

        val mergedWarnings = (result.monitoring.warnings + searchPreview.warnings.map(::toWarning))
            .distinctBy { "${it.code.wireValue()}:${it.detail}" }

        val mergedBottleneck = result.monitoring.bottleneck ?: searchPreview.bottleneck?.let(::toBottleneck)
        val mergedFailure = result.monitoring.failure ?: searchPreview.failure?.let(::toFailure)
        val mergedStage = if (mergedFailure != null) MonitoringStage.FAILED else result.monitoring.stage

        return result.copy(
            monitoring = result.monitoring.copy(
                stage = mergedStage,
                warnings = mergedWarnings,
                bottleneck = mergedBottleneck,
                failure = mergedFailure
            )
        )
    }

    fun agentFailure(request: ProductLookupRequest, message: String, retriable: Boolean = true): PriceComparisonResult =
        PriceComparisonResult(
            status = PriceComparisonStatus.COMPARISON_UNAVAILABLE,
            inputPrice = request.inputPrice.setScale(2, RoundingMode.HALF_UP),
            currency = request.currency,
            matchedProduct = null,
            bestOffer = null,
            savingsAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            savingsPercentage = null,
            consideredOfferCount = 0,
            excludedOfferCount = 0,
            explanationCode = ExplanationCode.AGENT_EXECUTION_FAILED,
            monitoring = RequestMonitoringSnapshot(
                stage = MonitoringStage.FAILED,
                progress = 20,
                failure = MonitoringFailure(
                    code = MonitoringFailureCode.AGENT_EXECUTION_FAILED,
                    message = message,
                    retriable = retriable
                )
            )
        )

    private fun toWarning(warning: MarketSearchWarning): MonitoringWarning = MonitoringWarning(
        code = when (warning.code.lowercase()) {
            "partial_results", "no_extractable_offer" -> MonitoringWarningCode.PARTIAL_RESULTS
            "unavailable_offers" -> MonitoringWarningCode.UNAVAILABLE_OFFERS
            else -> MonitoringWarningCode.INPUT_NORMALIZED
        },
        detail = warning.detail
    )

    private fun toBottleneck(bottleneck: MarketSearchBottleneck): MonitoringBottleneck = MonitoringBottleneck(
        stage = toStage(bottleneck.stage.name),
        reason = bottleneck.reason,
        detail = bottleneck.detail
    )

    private fun toFailure(failure: MarketSearchFailure): MonitoringFailure = MonitoringFailure(
        code = MonitoringFailureCode.PROVIDER_LOOKUP_FAILED,
        message = failure.message,
        retriable = failure.retriable
    )

    private fun toStage(stage: String): MonitoringStage = when (stage.lowercase()) {
        "search_discovery" -> MonitoringStage.SEARCH_DISCOVERY
        "offer_extraction" -> MonitoringStage.OFFER_EXTRACTION
        "matching" -> MonitoringStage.MATCHING
        "comparison" -> MonitoringStage.COMPARISON
        "completed" -> MonitoringStage.COMPLETED
        "failed" -> MonitoringStage.FAILED
        else -> MonitoringStage.OFFER_LOOKUP
    }
}
