package com.elegant.software.blitzpay.payments.truelayer.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TruelayerOpenApiConfig(private val apiVersionProperties: ApiVersionProperties) {

    @Bean
    fun truelayerApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("TrueLayer")
            .packagesToScan("com.elegant.software.blitzpay.payments.truelayer")
            .pathsToMatch("/{version}/webhooks/truelayer/**")
            .addOpenApiCustomizer { openApi ->
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.truelayer)
            }
            .build()
}
