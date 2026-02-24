package com.elegant.software.blitzpay.config

import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun paymentsGroup(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("General")
            .pathsToMatch("/v1/payments/**")
            .build()

    @Bean
    fun agentsGroup(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("Agents")
            .pathsToMatch("/v1/agents/**")
            .build()

    @Bean
    fun actuatorGroup(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("Actuator")
            .pathsToMatch("/actuator/**")
            .build()
}
