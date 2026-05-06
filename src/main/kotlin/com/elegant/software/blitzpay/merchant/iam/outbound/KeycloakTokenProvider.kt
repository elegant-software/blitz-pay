package com.elegant.software.blitzpay.merchant.iam.outbound

import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Component
@Profile("!contract-test")
class KeycloakTokenProvider(private val properties: KeycloakProperties) {

    private val log = LoggerFactory.getLogger(KeycloakTokenProvider::class.java)
    private val cached = AtomicReference<CachedToken?>()

    private val tokenClient: WebClient = WebClient.builder()
        .baseUrl(properties.serverUrl)
        .build()

    fun getToken(): Mono<String> {
        val current = cached.get()
        if (current != null && current.expiresAt.isAfter(Instant.now())) {
            return Mono.just(current.token)
        }
        return fetchToken()
    }

    fun invalidate() {
        cached.set(null)
        log.debug("keycloak token cache invalidated")
    }

    private fun fetchToken(): Mono<String> {
        return tokenClient.post()
            .uri("/realms/${properties.realm}/protocol/openid-connect/token")
            .body(
                BodyInserters.fromFormData("grant_type", "client_credentials")
                    .with("client_id", properties.adminClientId)
                    .with("client_secret", properties.adminClientSecret)
            )
            .retrieve()
            .bodyToMono<KeycloakTokenResponse>()
            .map { response ->
                val expiresAt = Instant.now().plusSeconds(response.expires_in - 30)
                cached.set(CachedToken(response.access_token, expiresAt))
                log.debug("keycloak token fetched expiresIn={}s", response.expires_in)
                response.access_token
            }
            .doOnError { e -> log.error("keycloak token fetch failed", e) }
    }

    private data class CachedToken(val token: String, val expiresAt: Instant)
}
