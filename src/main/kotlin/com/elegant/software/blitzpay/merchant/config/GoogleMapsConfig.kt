package com.elegant.software.blitzpay.merchant.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableScheduling
@EnableConfigurationProperties(GoogleMapsProperties::class, GeofenceProperties::class)
class GoogleMapsConfig {
    @Bean
    fun googleMapsWebClient(properties: GoogleMapsProperties): WebClient =
        WebClient.builder()
            .baseUrl(properties.baseUrl)
            .build()
}
