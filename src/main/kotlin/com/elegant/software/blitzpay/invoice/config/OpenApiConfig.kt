package com.elegant.software.blitzpay.invoice.config

import com.elegant.software.blitzpay.invoice.InvoiceController
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InvoiceOpenApiConfig {
    @Bean
    fun invoiceApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("Invoice")
            .packagesToScan(InvoiceController::class.java.packageName)
            .pathsToMatch("/{version}/invoices/**")
            .addOpenApiCustomizer { openApi: OpenAPI ->
                openApi.info = Info().title("BlitzPay — Invoice API").version("v1")
            }
            .build()
}
