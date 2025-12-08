package com.elegant.software.quickpay.truelayer.outbound

import com.elegant.software.quickpay.truelayer.support.TrueLayerProperties
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

@Service
class TrueLayerTokenService(
    private val trueLayerProperties: TrueLayerProperties,
    private val webClient: WebClient
) {

    fun fetchToken(): String {
        val response = webClient.post()
            .uri("https://auth.truelayer-sandbox.com/connect/token")
            .body(
                BodyInserters.fromFormData("grant_type", "client_credentials")
                    .with("client_id", trueLayerProperties.clientId)
                    .with("client_secret", trueLayerProperties.clientSecret)
                    .with("scope", "payments")
            )
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: throw RuntimeException("Empty response from TL token endpoint")

        return response["access_token"] as? String
            ?: throw RuntimeException("TrueLayer did not return access_token: $response")
    }

}