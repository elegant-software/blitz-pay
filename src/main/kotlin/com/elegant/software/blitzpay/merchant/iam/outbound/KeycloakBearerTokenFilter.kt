package com.elegant.software.blitzpay.merchant.iam.outbound

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono

class KeycloakBearerTokenFilter(private val tokenProvider: KeycloakTokenProvider) : ExchangeFilterFunction {

    private val log = LoggerFactory.getLogger(KeycloakBearerTokenFilter::class.java)

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> =
        tokenProvider.getToken()
            .flatMap { token -> next.exchange(withBearer(request, token)) }
            .flatMap { response ->
                if (response.statusCode() == HttpStatus.UNAUTHORIZED) {
                    log.warn("keycloak returned 401, retrying with fresh token method={} uri={}", request.method(), request.url())
                    tokenProvider.invalidate()
                    response.releaseBody()
                        .then(tokenProvider.getToken())
                        .flatMap { token -> next.exchange(withBearer(request, token)) }
                } else {
                    Mono.just(response)
                }
            }

    private fun withBearer(request: ClientRequest, token: String): ClientRequest =
        ClientRequest.from(request)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
}
