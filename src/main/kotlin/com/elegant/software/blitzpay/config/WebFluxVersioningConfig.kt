package com.elegant.software.blitzpay.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.ApiVersionConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * Configures Spring Framework 7 / Spring Boot 4 native API versioning for WebFlux.
 *
 * The API version is embedded in the URL path as the first segment (e.g. /v1/invoices).
 * Supported versions are auto-detected from the [version] attribute on each
 * [org.springframework.web.bind.annotation.RequestMapping].
 */
@Configuration
class WebFluxVersioningConfig : WebFluxConfigurer {

    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer
            .usePathSegment(0)
            .setDefaultVersion("1")
            .detectSupportedVersions(true)
    }
}
