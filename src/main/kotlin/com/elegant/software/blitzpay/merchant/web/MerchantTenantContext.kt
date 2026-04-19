package com.elegant.software.blitzpay.merchant.web

import reactor.core.publisher.Mono
import java.util.UUID

object MerchantTenantContext {
    const val KEY = "merchant.tenantId"

    fun currentMerchantId(): Mono<UUID> =
        Mono.deferContextual { ctx ->
            val id = ctx.getOrDefault<UUID>(KEY, null)
            if (id != null) Mono.just(id) else Mono.empty()
        }
}
