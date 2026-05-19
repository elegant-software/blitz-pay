package com.elegant.software.blitzpay.betterprice.agent.tool

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
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.RequestMonitoringSnapshot
import java.math.BigDecimal

object TestPriceComparisonFixtures {
    fun betterPriceResult(): PriceComparisonResult = PriceComparisonResult(
        status = PriceComparisonStatus.BETTER_PRICE_FOUND,
        inputPrice = BigDecimal("329.99"),
        currency = "USD",
        matchedProduct = MatchedProduct(
            normalizedTitle = "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
            brandName = "Sony",
            modelName = "WH-1000XM5",
            sku = "SONY-WH1000XM5-BLK",
            matchConfidence = MatchConfidence.HIGH,
            matchEvidence = listOf("brand", "model", "sku", "title")
        ),
        bestOffer = ComparableOffer(
            sellerName = "TechHub",
            offerPrice = BigDecimal("279.99"),
            currency = "USD",
            productUrl = "https://example.test/offers/sony-wh1000xm5-techhub",
            availability = true,
            qualificationStatus = OfferQualificationStatus.QUALIFIED,
            qualificationNotes = "Offer matched the requested product strongly enough to be compared"
        ),
        savingsAmount = BigDecimal("50.00"),
        savingsPercentage = BigDecimal("15.15"),
        consideredOfferCount = 2,
        excludedOfferCount = 0,
        explanationCode = ExplanationCode.BETTER_PRICE_AVAILABLE,
        monitoring = RequestMonitoringSnapshot(
            stage = MonitoringStage.COMPLETED,
            progress = 100,
            warnings = emptyList(),
            bottleneck = null,
            failure = null
        )
    )

    fun noBetterPriceResult(): PriceComparisonResult = PriceComparisonResult(
        status = PriceComparisonStatus.NO_BETTER_PRICE_FOUND,
        inputPrice = BigDecimal("549.99"),
        currency = "USD",
        matchedProduct = MatchedProduct(
            normalizedTitle = "Dyson Airwrap Multi-Styler Complete Long",
            brandName = "Dyson",
            modelName = "Airwrap Complete Long",
            sku = "DYS-AIRWRAP-COMPLETE-LONG",
            matchConfidence = MatchConfidence.HIGH,
            matchEvidence = listOf("brand", "model", "sku", "title")
        ),
        bestOffer = null,
        savingsAmount = BigDecimal("0.00"),
        savingsPercentage = BigDecimal("0.00"),
        consideredOfferCount = 2,
        excludedOfferCount = 0,
        explanationCode = ExplanationCode.NO_CHEAPER_QUALIFYING_OFFER,
        monitoring = RequestMonitoringSnapshot(
            stage = MonitoringStage.COMPLETED,
            progress = 100,
            warnings = emptyList(),
            bottleneck = null,
            failure = null
        )
    )

    fun comparisonUnavailableResult(): PriceComparisonResult = PriceComparisonResult(
        status = PriceComparisonStatus.COMPARISON_UNAVAILABLE,
        inputPrice = BigDecimal("89.99"),
        currency = "USD",
        matchedProduct = null,
        bestOffer = null,
        savingsAmount = BigDecimal("0.00"),
        savingsPercentage = null,
        consideredOfferCount = 0,
        excludedOfferCount = 2,
        explanationCode = ExplanationCode.LOW_MATCH_CONFIDENCE,
        monitoring = RequestMonitoringSnapshot(
            stage = MonitoringStage.COMPLETED,
            progress = 100,
            warnings = listOf(
                MonitoringWarning(
                    code = MonitoringWarningCode.LOW_MATCH_CONFIDENCE,
                    detail = "The request matched too weakly against available offers to make a reliable comparison"
                )
            ),
            bottleneck = null,
            failure = null
        )
    )

    fun providerFailureResult(): PriceComparisonResult = PriceComparisonResult(
        status = PriceComparisonStatus.COMPARISON_UNAVAILABLE,
        inputPrice = BigDecimal("329.99"),
        currency = "USD",
        matchedProduct = null,
        bestOffer = null,
        savingsAmount = BigDecimal("0.00"),
        savingsPercentage = null,
        consideredOfferCount = 0,
        excludedOfferCount = 0,
        explanationCode = ExplanationCode.PROVIDER_LOOKUP_FAILED,
        monitoring = RequestMonitoringSnapshot(
            stage = MonitoringStage.FAILED,
            progress = 55,
            warnings = listOf(
                MonitoringWarning(
                    code = MonitoringWarningCode.PARTIAL_RESULTS,
                    detail = "Lookup aborted before a complete retailer set could be inspected"
                )
            ),
            bottleneck = MonitoringBottleneck(
                stage = MonitoringStage.SEARCH_DISCOVERY,
                reason = "provider_timeout",
                detail = "External retailer lookup exceeded the synchronous request budget"
            ),
            failure = MonitoringFailure(
                code = MonitoringFailureCode.PROVIDER_LOOKUP_FAILED,
                message = "Retailer lookup failed before a comparable offer set was assembled",
                retriable = true
            )
        )
    )
}
