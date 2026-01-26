package com.elegant.software.blitzpay.payments.support.config


import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SupportOpenApiConfig {
    @Bean
    fun supportApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("support")
            .packagesToScan("com.elegant.software.blitzpay.payments.support")
            .pathsToMatch("/support/**")
            .build()
}
