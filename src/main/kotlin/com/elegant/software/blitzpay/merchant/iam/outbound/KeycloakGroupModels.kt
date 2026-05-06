package com.elegant.software.blitzpay.merchant.iam.outbound

data class KeycloakGroupBody(
    val name: String,
    val attributes: Map<String, List<String>> = emptyMap(),
)

data class KeycloakGroupRepresentation(
    val id: String,
    val name: String,
    val path: String = "",
    val attributes: Map<String, List<String>>? = null,
)

data class KeycloakTokenResponse(
    val access_token: String,
    val expires_in: Long,
)
