package com.elegant.software.blitzpay.payments.push.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "blitzpay.expo")
data class ExpoPushProperties(
    val accessToken: String = "",
    val baseUrl: String = "https://exp.host/--/api/v2/push/send",
    val receiptsUrl: String = "https://exp.host/--/api/v2/push/getReceipts",
    val requestTimeoutMs: Long = 10_000,
    val maxBatchSize: Int = 100,
    val receiptDelayMinutes: Long = 15,
)
