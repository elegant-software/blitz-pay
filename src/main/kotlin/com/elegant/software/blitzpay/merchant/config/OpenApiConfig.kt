package com.elegant.software.blitzpay.merchant.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import com.elegant.software.blitzpay.merchant.web.MerchantOnboardingController
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MerchantOpenApiConfig(private val apiVersionProperties: ApiVersionProperties) {

    @Bean
    fun merchantApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("Merchant")
            .packagesToScan(MerchantOnboardingController::class.java.packageName)
            .pathsToMatch("/{version}/merchants/**")
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info().title("BlitzPay — Merchant Onboarding API").version("v${apiVersionProperties.versions.merchant}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.merchant)
            }
            .build()
}
