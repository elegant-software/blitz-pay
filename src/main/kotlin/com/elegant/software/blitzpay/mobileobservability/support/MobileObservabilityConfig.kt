package com.elegant.software.blitzpay.mobileobservability.support

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(MobileObservabilityProperties::class)
class MobileObservabilityConfig {

    @Bean
    @ConditionalOnMissingBean(WebClient.Builder::class)
    fun otlpWebClientBuilder(): WebClient.Builder = WebClient.builder()

    @Bean
    fun otlpWebClient(builder: WebClient.Builder): WebClient = builder.build()
}
