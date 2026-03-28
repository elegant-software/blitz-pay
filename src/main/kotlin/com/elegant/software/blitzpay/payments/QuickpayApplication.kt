package com.elegant.software.blitzpay.payments

import com.elegant.software.blitzpay.config.ApiVersionProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.elegant.software.blitzpay"])
@EnableConfigurationProperties(ApiVersionProperties::class)
class QuickpayApplication

fun main(args: Array<String>) {
	runApplication<QuickpayApplication>(*args)
}
