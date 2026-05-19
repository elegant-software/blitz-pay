package com.elegant.software.blitzpay.betterprice.agent.tool

import com.elegant.software.blitzpay.betterprice.agent.api.PriceComparisonChatRequest
import com.elegant.software.blitzpay.betterprice.agent.api.toDomain
import com.elegant.software.blitzpay.betterprice.pricecomparison.api.PriceComparisonGateway
import com.elegant.software.blitzpay.betterprice.pricecomparison.domain.PriceComparisonResult
import org.springframework.stereotype.Component

@Component
class PriceComparisonToolAdapter(
    private val priceComparisonGateway: PriceComparisonGateway
) {

    fun compare(request: PriceComparisonChatRequest): PriceComparisonResult =
        priceComparisonGateway.compare(request.toDomain())
}
