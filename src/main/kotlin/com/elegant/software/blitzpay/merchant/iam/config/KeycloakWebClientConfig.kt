package com.elegant.software.blitzpay.merchant.iam.config

import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakBearerTokenFilter
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupClient
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakTokenProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

@Configuration
@Profile("!contract-test")
@EnableConfigurationProperties(KeycloakProperties::class)
class KeycloakWebClientConfig {

    @Bean
    fun keycloakGroupClient(
        properties: KeycloakProperties,
        tokenProvider: KeycloakTokenProvider,
    ): KeycloakGroupClient {
        val webClient = WebClient.builder()
            .baseUrl("${properties.serverUrl}/admin/realms/${properties.realm}")
            .filter(KeycloakBearerTokenFilter(tokenProvider))
            .build()
        return HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient))
            .build()
            .createClient(KeycloakGroupClient::class.java)
    }
}
