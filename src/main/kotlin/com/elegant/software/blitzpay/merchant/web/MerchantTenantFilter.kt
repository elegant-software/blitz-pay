package com.elegant.software.blitzpay.merchant.web

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class MerchantTenantFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val merchantId = extractMerchantId(exchange.request)
            ?: return chain.filter(exchange)

        return chain.filter(exchange)
            .contextWrite { ctx -> ctx.put(MerchantTenantContext.KEY, merchantId) }
    }

    private fun extractMerchantId(request: ServerHttpRequest): UUID? {
        val segments = request.path.pathWithinApplication().value()
            .split("/")
            .filter { it.isNotEmpty() }

        val merchantsIndex = segments.indexOfFirst { it == "merchants" }
        if (merchantsIndex < 0 || merchantsIndex + 1 >= segments.size) return null

        return runCatching { UUID.fromString(segments[merchantsIndex + 1]) }.getOrNull()
    }
}
