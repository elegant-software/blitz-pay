package com.elegant.software.quickpay

import com.elegant.software.quickpay.truelayer.support.QrCodeProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(QrCodeProperties::class)
@EnableScheduling
class QuickpayApplication

fun main(args: Array<String>) {
    runApplication<QuickpayApplication>(*args)
}
