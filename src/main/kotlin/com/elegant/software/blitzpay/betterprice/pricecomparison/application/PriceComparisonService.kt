package com.elegant.software.blitzpay.betterprice.pricecomparison.application

import com.elegant.software.blitzpay.betterprice.pricecomparison.api.PriceComparisonGateway
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.*
import com.elegant.software.blitzpay.betterprice.pricecomparison.provider.PriceLookupProvider
import com.elegant.software.blitzpay.betterprice.pricecomparison.provider.ProviderLookupBottleneck
import com.elegant.software.blitzpay.betterprice.pricecomparison.provider.ProviderLookupWarning
import org.springframework.stereotype.Service

@Service
class PriceComparisonService(
    private val validation: PriceComparisonValidation,
    private val provider: PriceLookupProvider,
    private val matchingService: ProductMatchingService,
    private val outcomeFactory: PriceComparisonOutcomeFactory
) : PriceComparisonGateway {

    override fun compare(request: ProductLookupRequest): PriceComparisonResult {
        try {
            validation.validate(request)
        } catch (ex: IllegalArgumentException) {
            return outcomeFactory.comparisonUnavailable(
                request = request,
                explanationCode = ExplanationCode.INVALID_REQUEST,
                monitoring = validation.failedSnapshot(
                    stage = MonitoringStage.VALIDATION,
                    progress = 10,
                    code = MonitoringFailureCode.INVALID_REQUEST,
                    message = ex.message ?: "Price comparison request validation failed"
                )
            )
        }

        return runCatching {
            val lookup = provider.findOffers(request)
            val warnings = lookup.warnings.map(::toMonitoringWarning).toMutableList()
            val bottleneck = lookup.bottleneck?.toMonitoringBottleneck()

            lookup.failure?.let { failure ->
                return outcomeFactory.comparisonUnavailable(
                    request = request,
                    explanationCode = ExplanationCode.PROVIDER_LOOKUP_FAILED,
                    monitoring = validation.failedSnapshot(
                        stage = failure.stage.toMonitoringStage(),
                        progress = 55,
                        code = MonitoringFailureCode.PROVIDER_LOOKUP_FAILED,
                        message = failure.message,
                        warnings = warnings,
                        bottleneck = bottleneck,
                        retriable = failure.retriable
                    )
                )
            }

            if (lookup.offers.isEmpty()) {
                return outcomeFactory.comparisonUnavailable(
                    request = request,
                    explanationCode = ExplanationCode.NO_COMPARABLE_OFFERS,
                    monitoring = validation.completedSnapshot(
                        warnings = warnings,
                        bottleneck = bottleneck
                    )
                )
            }

            val resolution = matchingService.resolve(request, lookup.offers)
            val matchedProduct = resolution.matchedProduct
                ?: return outcomeFactory.comparisonUnavailable(
                    request = request,
                    explanationCode = ExplanationCode.NO_COMPARABLE_OFFERS,
                    monitoring = validation.completedSnapshot(
                        warnings = warnings,
                        bottleneck = bottleneck
                    )
                )

            if (resolution.confidence == MatchConfidence.LOW) {
                warnings += MonitoringWarning(
                    code = MonitoringWarningCode.LOW_MATCH_CONFIDENCE,
                    detail = "The request matched too weakly against available offers to make a reliable comparison"
                )
                return outcomeFactory.comparisonUnavailable(
                    request = request,
                    explanationCode = ExplanationCode.LOW_MATCH_CONFIDENCE,
                    matchedProduct = matchedProduct,
                    consideredOffers = resolution.consideredOffers,
                    excludedOffers = resolution.excludedOffers,
                    monitoring = validation.completedSnapshot(
                        warnings = warnings,
                        bottleneck = bottleneck
                    )
                )
            }

            val qualifiedLowerOffers = resolution.consideredOffers.filter { offer ->
                offer.qualificationStatus == OfferQualificationStatus.QUALIFIED &&
                    offer.currency.equals(request.currency, ignoreCase = true) &&
                    offer.offerPrice < request.inputPrice
            }

            val bestOffer = qualifiedLowerOffers.minByOrNull { it.offerPrice }
            if (bestOffer != null) {
                outcomeFactory.betterPriceFound(
                    request = request,
                    matchedProduct = matchedProduct,
                    bestOffer = bestOffer,
                    consideredOffers = resolution.consideredOffers,
                    excludedOffers = resolution.excludedOffers,
                    monitoring = validation.completedSnapshot(
                        warnings = warnings,
                        bottleneck = bottleneck
                    )
                )
            } else {
                outcomeFactory.noBetterPriceFound(
                    request = request,
                    matchedProduct = matchedProduct,
                    consideredOffers = resolution.consideredOffers,
                    excludedOffers = resolution.excludedOffers,
                    monitoring = validation.completedSnapshot(
                        warnings = warnings,
                        bottleneck = bottleneck
                    )
                )
            }
        }.getOrElse { ex ->
            outcomeFactory.comparisonUnavailable(
                request = request,
                explanationCode = ExplanationCode.PROVIDER_LOOKUP_FAILED,
                monitoring = validation.failedSnapshot(
                    stage = MonitoringStage.FAILED,
                    progress = 90,
                    code = MonitoringFailureCode.UNEXPECTED_ERROR,
                    message = ex.message ?: "Unexpected comparison failure"
                )
            )
        }
    }

    private fun toMonitoringWarning(warning: ProviderLookupWarning): MonitoringWarning = MonitoringWarning(
        code = when (warning.code.lowercase()) {
            "partial_results" -> MonitoringWarningCode.PARTIAL_RESULTS
            "no_extractable_offer" -> MonitoringWarningCode.PARTIAL_RESULTS
            "unavailable_offers" -> MonitoringWarningCode.UNAVAILABLE_OFFERS
            else -> MonitoringWarningCode.INPUT_NORMALIZED
        },
        detail = warning.detail
    )

    private fun ProviderLookupBottleneck.toMonitoringBottleneck(): MonitoringBottleneck = MonitoringBottleneck(
        stage = stage.toMonitoringStage(),
        reason = reason,
        detail = detail
    )

    private fun String.toMonitoringStage(): MonitoringStage = when (lowercase()) {
        "search_discovery" -> MonitoringStage.SEARCH_DISCOVERY
        "offer_extraction" -> MonitoringStage.OFFER_EXTRACTION
        "matching" -> MonitoringStage.MATCHING
        "comparison" -> MonitoringStage.COMPARISON
        else -> MonitoringStage.OFFER_LOOKUP
    }
}
