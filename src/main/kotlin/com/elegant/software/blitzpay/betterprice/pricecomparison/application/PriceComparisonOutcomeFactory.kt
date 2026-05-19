package com.elegant.software.blitzpay.betterprice.pricecomparison.application

import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ComparableOffer
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ExplanationCode
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MatchedProduct
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.OfferQualificationStatus
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.PriceComparisonResult
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.PriceComparisonStatus
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ProductLookupRequest
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.RequestMonitoringSnapshot
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class PriceComparisonOutcomeFactory(
    private val validation: PriceComparisonValidation
) {

    fun betterPriceFound(
        request: ProductLookupRequest,
        matchedProduct: MatchedProduct,
        bestOffer: ComparableOffer,
        consideredOffers: List<ComparableOffer>,
        excludedOffers: List<ComparableOffer>,
        monitoring: RequestMonitoringSnapshot
    ): PriceComparisonResult {
        val savings = request.inputPrice.subtract(bestOffer.offerPrice).setScale(2, RoundingMode.HALF_UP)
        return PriceComparisonResult(
            status = PriceComparisonStatus.BETTER_PRICE_FOUND,
            inputPrice = request.inputPrice.setScale(2, RoundingMode.HALF_UP),
            currency = request.currency,
            matchedProduct = matchedProduct,
            bestOffer = bestOffer.copy(qualificationStatus = OfferQualificationStatus.QUALIFIED),
            savingsAmount = savings,
            savingsPercentage = validation.calculateSavingsPercentage(request.inputPrice, bestOffer.offerPrice),
            consideredOfferCount = consideredOffers.size,
            excludedOfferCount = excludedOffers.size,
            explanationCode = ExplanationCode.BETTER_PRICE_AVAILABLE,
            monitoring = monitoring
        )
    }

    fun noBetterPriceFound(
        request: ProductLookupRequest,
        matchedProduct: MatchedProduct,
        consideredOffers: List<ComparableOffer>,
        excludedOffers: List<ComparableOffer>,
        monitoring: RequestMonitoringSnapshot
    ): PriceComparisonResult = PriceComparisonResult(
        status = PriceComparisonStatus.NO_BETTER_PRICE_FOUND,
        inputPrice = request.inputPrice.setScale(2, RoundingMode.HALF_UP),
        currency = request.currency,
        matchedProduct = matchedProduct,
        bestOffer = null,
        savingsAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
        savingsPercentage = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
        consideredOfferCount = consideredOffers.size,
        excludedOfferCount = excludedOffers.size,
        explanationCode = ExplanationCode.NO_CHEAPER_QUALIFYING_OFFER,
        monitoring = monitoring
    )

    fun comparisonUnavailable(
        request: ProductLookupRequest,
        explanationCode: ExplanationCode,
        matchedProduct: MatchedProduct? = null,
        consideredOffers: List<ComparableOffer> = emptyList(),
        excludedOffers: List<ComparableOffer> = emptyList(),
        monitoring: RequestMonitoringSnapshot
    ): PriceComparisonResult = PriceComparisonResult(
        status = PriceComparisonStatus.COMPARISON_UNAVAILABLE,
        inputPrice = request.inputPrice.setScale(2, RoundingMode.HALF_UP),
        currency = request.currency,
        matchedProduct = matchedProduct,
        bestOffer = null,
        savingsAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
        savingsPercentage = null,
        consideredOfferCount = consideredOffers.size,
        excludedOfferCount = excludedOffers.size,
        explanationCode = explanationCode,
        monitoring = monitoring
    )
}
