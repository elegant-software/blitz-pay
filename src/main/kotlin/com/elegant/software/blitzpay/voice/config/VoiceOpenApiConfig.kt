package com.elegant.software.blitzpay.voice.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.OpenApiGroupProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VoiceOpenApiConfig(
    private val apiVersionProperties: ApiVersionProperties,
    private val openApiGroupProperties: OpenApiGroupProperties,
) {

    @Bean
    fun voiceApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.voice.label)
            .packagesToScan("com.elegant.software.blitzpay.voice")
            .pathsToMatch("/{version}/voice/**")
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info()
                    .title(openApiGroupProperties.groups.voice.label)
                    .version("v${apiVersionProperties.versions.voice}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.voice)
            }
            .build()
}
