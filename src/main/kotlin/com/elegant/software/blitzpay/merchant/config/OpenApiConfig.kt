package com.elegant.software.blitzpay.merchant.config

import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MerchantOpenApiConfig {
    @Bean
    fun merchantApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("Merchant")
            .packagesToScan(MerchantGateway::class.java.packageName)
            .pathsToMatch("/v1/merchant-onboarding/**", "/v1/merchants/**")
            .addOpenApiCustomizer { openApi: OpenAPI ->
                openApi.info = Info().title("BlitzPay — Merchant Onboarding API").version("v1")
            }
            .build()
}
