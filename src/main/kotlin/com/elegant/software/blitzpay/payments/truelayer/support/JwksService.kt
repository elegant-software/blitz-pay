// src/main/kotlin/com/example/tl/JwksService.kt
package com.elegant.software.blitzpay.payments.truelayer.support

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration
@EnableConfigurationProperties(TlWebhookProperties::class)
@Service

class JwksService(
    private val props: TlWebhookProperties
) {
    private val client = WebClient.builder().build()
    @Volatile private var cached: String? = null
    @Volatile private var cachedAt: Long = 0

    fun fetchJwks(): Mono<String> {
        val now = System.currentTimeMillis()
        val refreshMs = Duration.ofMinutes(10).toMillis()
        val current = cached
        if (current != null && (now - cachedAt) < refreshMs) return Mono.just(current)

        return client.get()
            .uri(props.allowedJku)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(String::class.java)
            .doOnNext {
                cached = it
                cachedAt = System.currentTimeMillis()
            }
    }
}
