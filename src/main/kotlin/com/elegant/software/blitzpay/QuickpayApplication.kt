package com.elegant.software.blitzpay.payments

import com.elegant.software.blitzpay.config.ApiVersionProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.elegant.software.blitzpay"])
@EnableJpaRepositories(basePackages = ["com.elegant.software.blitzpay"])
@EntityScan(basePackages = ["com.elegant.software.blitzpay"])
@EnableConfigurationProperties(ApiVersionProperties::class)
class QuickpayApplication

fun main(args: Array<String>) {
	runApplication<QuickpayApplication>(*args)
}
