package com.elegant.software.blitzpay.payments.truelayer.config


import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TruelayerOpenApiConfig {
    @Bean
    fun truelayerApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("TrueLayer")
            // scan only the truelayer package (keeps module boundaries intact)
            .packagesToScan("com.elegant.software.blitzpay.payments.truelayer")
            // the webhook controller is mounted at /webhooks/truelayer
            .pathsToMatch("/webhooks/truelayer/**")
            .build()
}
