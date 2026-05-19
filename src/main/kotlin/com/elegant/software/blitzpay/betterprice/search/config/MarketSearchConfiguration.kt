package com.elegant.software.blitzpay.betterprice.search.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(MarketSearchProperties::class)
class MarketSearchConfiguration {

    @Bean
    fun marketSearchWebClient(): WebClient = WebClient.create()
}
