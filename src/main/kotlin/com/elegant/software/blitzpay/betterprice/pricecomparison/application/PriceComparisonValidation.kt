package com.elegant.software.blitzpay.betterprice.pricecomparison.application

import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringBottleneck
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringFailure
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringFailureCode
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringStage
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.MonitoringWarning
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.ProductLookupRequest
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.RequestMonitoringSnapshot
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class PriceComparisonValidation {

    fun validate(request: ProductLookupRequest) {
        require(request.inputPrice > BigDecimal.ZERO) { "inputPrice must be a positive monetary amount" }
        require(request.currency.isNotBlank()) { "currency must not be blank" }
        require(hasAnyIdentifier(request)) {
            "at least one product identifier must be provided"
        }
    }

    fun calculateSavingsPercentage(inputPrice: BigDecimal, lowerPrice: BigDecimal): BigDecimal =
        inputPrice.subtract(lowerPrice)
            .divide(inputPrice, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)

    fun snapshot(
        stage: MonitoringStage,
        progress: Int,
        warnings: List<MonitoringWarning> = emptyList(),
        bottleneck: MonitoringBottleneck? = null,
        failure: MonitoringFailure? = null
    ): RequestMonitoringSnapshot = RequestMonitoringSnapshot(
        stage = stage,
        progress = progress.coerceIn(0, 100),
        warnings = warnings,
        bottleneck = bottleneck,
        failure = failure
    )

    fun completedSnapshot(
        warnings: List<MonitoringWarning> = emptyList(),
        bottleneck: MonitoringBottleneck? = null
    ): RequestMonitoringSnapshot = snapshot(
        stage = MonitoringStage.COMPLETED,
        progress = 100,
        warnings = warnings,
        bottleneck = bottleneck
    )

    fun failedSnapshot(
        stage: MonitoringStage,
        progress: Int,
        code: MonitoringFailureCode,
        message: String,
        warnings: List<MonitoringWarning> = emptyList(),
        bottleneck: MonitoringBottleneck? = null,
        retriable: Boolean = false
    ): RequestMonitoringSnapshot = snapshot(
        stage = MonitoringStage.FAILED,
        progress = progress.coerceIn(0, 100),
        warnings = warnings,
        bottleneck = bottleneck,
        failure = MonitoringFailure(
            code = code,
            message = message,
            retriable = retriable
        )
    )

    private fun hasAnyIdentifier(request: ProductLookupRequest): Boolean =
        listOf(request.productTitle, request.brandName, request.modelName, request.sku)
            .any { !it.isNullOrBlank() } || request.additionalAttributes.isNotEmpty()
}
