package com.elegant.software.blitzpay.config

import jakarta.annotation.PostConstruct
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties
import org.springdoc.core.properties.SwaggerUiConfigProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet

@Configuration
class SwaggerUiGroupsConfig(
    private val swaggerUiConfigProperties: SwaggerUiConfigProperties,
    private val openApiGroupProperties: OpenApiGroupProperties,
) {

    @PostConstruct
    fun configureSwaggerUiGroups() {
        val urls = listOf(
            openApiGroupProperties.groups.invoice,
            openApiGroupProperties.groups.qrpay,
            openApiGroupProperties.groups.truelayer,
            openApiGroupProperties.groups.support,
            openApiGroupProperties.groups.general,
            openApiGroupProperties.groups.pushNotifications,
            openApiGroupProperties.groups.stripe,
            openApiGroupProperties.groups.braintree,
            openApiGroupProperties.groups.mobileGeofencing,
            openApiGroupProperties.groups.merchant,
            openApiGroupProperties.groups.actuator,
        ).map { group ->
            val encodedLabel = UriUtils.encodePathSegment(group.label, StandardCharsets.UTF_8)
            AbstractSwaggerUiConfigProperties.SwaggerUrl(
                group.label,
                "/api-docs/$encodedLabel",
                group.label,
            )
        }

        swaggerUiConfigProperties.urls = LinkedHashSet(urls)
        swaggerUiConfigProperties.urlsPrimaryName = openApiGroupProperties.primaryGroupLabel
    }
}
