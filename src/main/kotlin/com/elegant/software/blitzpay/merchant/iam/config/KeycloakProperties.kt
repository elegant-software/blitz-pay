package com.elegant.software.blitzpay.merchant.iam.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keycloak.iam")
data class KeycloakProperties(
    val serverUrl: String,
    val realm: String,
    val adminClientId: String,
    val adminClientSecret: String,
)
