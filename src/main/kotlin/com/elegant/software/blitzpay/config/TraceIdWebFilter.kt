package com.elegant.software.blitzpay.config

import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Assigns a per-request correlation id (`traceId`) and exposes it via:
 *  - Reactor Context under key [TRACE_ID_KEY]
 *  - SLF4J MDC (best-effort — relies on Spring Boot virtual-thread executor or
 *    synchronous handlers; fully reactive chains should use [Mono.deferContextual])
 *  - Response header `X-Request-Id`
 *
 * Honours an incoming `X-Request-Id` header so upstream ids (TrueLayer webhook
 * id, load balancer ids, etc.) are preserved.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdWebFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val incoming = exchange.request.headers.getFirst(REQUEST_ID_HEADER)
            ?: exchange.request.headers.getFirst("x-tl-webhook-id")
        val traceId = incoming?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        exchange.response.headers.set(REQUEST_ID_HEADER, traceId)

        MDC.put(TRACE_ID_KEY, traceId)
        return chain.filter(exchange)
            .contextWrite { ctx -> ctx.put(TRACE_ID_KEY, traceId) }
            .doFinally { MDC.remove(TRACE_ID_KEY) }
    }

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val REQUEST_ID_HEADER = "X-Request-Id"
    }
}
