package com.elegant.software.blitzpay.payments.qrpay.config


import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QrpayOpenApiConfig {
    @Bean
    fun qrpayApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("QRPay")
            .packagesToScan("com.elegant.software.blitzpay.payments.qrpay")
            .pathsToMatch("/payments/**", "/qr-payments/**", "/api/qr-payments/**")
            .addOpenApiCustomizer { openApi: OpenAPI ->
                openApi.info = Info().title("BlitzPay — QR Payments API").version("v1")
            }
            .build()
}
