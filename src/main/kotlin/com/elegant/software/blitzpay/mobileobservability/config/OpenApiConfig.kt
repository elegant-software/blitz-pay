package com.elegant.software.blitzpay.mobileobservability.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MobileObservabilityOpenApiConfig(private val apiVersionProperties: ApiVersionProperties) {

    @Bean
    fun mobileObservabilityApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("MobileObservability")
            .packagesToScan("com.elegant.software.blitzpay.mobileobservability")
            .pathsToMatch("/{version}/observability/**")
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info()
                    .title("BlitzPay — Mobile Observability API")
                    .version("v${apiVersionProperties.versions.mobileObservability}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.mobileObservability)
            }
            .build()
}
