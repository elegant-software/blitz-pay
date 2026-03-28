package com.elegant.software.blitzpay.config

import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig(private val apiVersionProperties: ApiVersionProperties) {

    @Bean
    fun paymentsGroup(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("General")
            .pathsToMatch("/{version}/payments/**")
            .addOpenApiCustomizer { openApi ->
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.payments)
            }
            .build()

    @Bean
    fun actuatorGroup(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("Actuator")
            .pathsToMatch("/actuator/**")
            .build()
}
