package com.elegant.software.blitzpay.config

import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun paymentsGroup(): GroupedOpenApi =
        GroupedOpenApi.builder()
            // Use a group name that matches the @Tag on the controller
            .group("General")
            // adjust to your controllers’ paths
            .pathsToMatch("/payments/**")
            .build()

    @Bean
    fun actuatorGroup(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("Actuator")
            .pathsToMatch("/actuator/**")
            .build()
}