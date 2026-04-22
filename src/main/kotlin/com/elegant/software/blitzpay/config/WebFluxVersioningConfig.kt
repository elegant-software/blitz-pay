package com.elegant.software.blitzpay.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.server.PathContainer
import org.springframework.web.reactive.accept.ApiVersionResolver
import org.springframework.web.reactive.config.ApiVersionConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
class WebFluxVersioningConfig(
    private val apiVersionProperties: ApiVersionProperties
) : WebFluxConfigurer {

    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer
            .useVersionResolver(PathOnlyApiVersionResolver())
            .setVersionRequired(false)
            .setDefaultVersion(apiVersionProperties.defaultVersion)
            .detectSupportedVersions(true)
    }
}

class PathOnlyApiVersionResolver : ApiVersionResolver {
    private val versionPattern = Regex("^v(\\d+(?:\\.\\d+)*)$")

    override fun resolveVersion(exchange: org.springframework.web.server.ServerWebExchange): String? {
        val path = exchange.request.path.value()

        // ✅ EXCLUDE MCP endpoint completely
        if (path.startsWith("/mcp")) {
            return null
        }

        val firstSegment = exchange.request.path.pathWithinApplication().elements()
            .filterIsInstance<PathContainer.PathSegment>()
            .firstOrNull()
            ?.valueToMatch()
            ?: return null

        val match = versionPattern.matchEntire(firstSegment) ?: return null
        return match.groupValues[1]
    }
}
