package com.elegant.software.blitzpay.payments.qrpay.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QrpayOpenApiConfig(private val apiVersionProperties: ApiVersionProperties) {

    @Bean
    fun qrpayApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("QRPay")
            .packagesToScan("com.elegant.software.blitzpay.payments.qrpay")
            .pathsToMatch("/{version}/payments/**", "/{version}/qr-payments/**")
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info().title("BlitzPay — Payments API").version("v${apiVersionProperties.versions.qrpay}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.qrpay)
            }
            .build()
}
