package com.elegant.software.blitzpay.payments.braintree.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.OpenApiGroupProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BraintreeOpenApiConfig(
    private val apiVersionProperties: ApiVersionProperties,
    private val openApiGroupProperties: OpenApiGroupProperties,
) {

    @Bean
    fun braintreeApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.braintree.label)
            .packagesToScan("com.elegant.software.blitzpay.payments.braintree")
            .pathsToMatch("/{version}/payments/braintree/**")
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info()
                    .title(openApiGroupProperties.groups.braintree.label)
                    .version("v${apiVersionProperties.versions.payments}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.payments)
            }
            .build()
}
