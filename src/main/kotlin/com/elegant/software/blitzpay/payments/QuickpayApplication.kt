package com.elegant.software.blitzpay.payments

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication (scanBasePackages = ["com.elegant.software.blitzpay"] )
class QuickpayApplication

fun main(args: Array<String>) {
runApplication<QuickpayApplication>(*args)
}
