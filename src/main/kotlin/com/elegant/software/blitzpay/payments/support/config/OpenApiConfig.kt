package com.elegant.software.blitzpay.payments.support.config

import com.elegant.software.blitzpay.config.OpenApiGroupProperties
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SupportOpenApiConfig(private val openApiGroupProperties: OpenApiGroupProperties) {
    @Bean
    fun supportApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.support.label)
            .packagesToScan("com.elegant.software.blitzpay.payments.support")
            .pathsToMatch("/support/**")
            .build()
}
