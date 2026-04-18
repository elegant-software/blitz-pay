package com.elegant.software.blitzpay.payments.braintree.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BraintreeOpenApiConfig(private val apiVersionProperties: ApiVersionProperties) {

    @Bean
    fun braintreeApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("Braintree")
            .packagesToScan("com.elegant.software.blitzpay.payments.braintree")
            .pathsToMatch("/{version}/payments/braintree/**")
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info()
                    .title("BlitzPay — Braintree PayPal / Digital Wallet")
                    .version("v${apiVersionProperties.versions.payments}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.payments)
            }
            .build()
}
