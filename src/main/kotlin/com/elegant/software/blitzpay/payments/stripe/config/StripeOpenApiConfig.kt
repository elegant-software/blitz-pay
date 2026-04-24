package com.elegant.software.blitzpay.payments.stripe.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.OpenApiGroupProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class StripeOpenApiConfig(
    private val apiVersionProperties: ApiVersionProperties,
    private val openApiGroupProperties: OpenApiGroupProperties,
) {

    @Bean
    fun stripeApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.stripe.label)
            .packagesToScan("com.elegant.software.blitzpay.payments.stripe")
            .pathsToMatch("/{version}/payments/stripe/**")
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info()
                    .title(openApiGroupProperties.groups.stripe.label)
                    .version("v${apiVersionProperties.versions.payments}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.payments)
            }
            .build()
}
