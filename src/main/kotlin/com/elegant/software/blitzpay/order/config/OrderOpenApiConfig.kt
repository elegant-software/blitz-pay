package com.elegant.software.blitzpay.order.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.OpenApiGroupProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import com.elegant.software.blitzpay.order.web.ShopperOrderController
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OrderOpenApiConfig(
    private val apiVersionProperties: ApiVersionProperties,
    private val openApiGroupProperties: OpenApiGroupProperties,
) {

    @Bean
    fun orderApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.order.label)
            .packagesToScan(ShopperOrderController::class.java.packageName)
            .pathsToMatch("/{version}/orders/**", "/{version}/merchant/orders/**")
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info().title("BlitzPay — Orders API").version("v${apiVersionProperties.versions.order}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.order)
            }
            .build()
}