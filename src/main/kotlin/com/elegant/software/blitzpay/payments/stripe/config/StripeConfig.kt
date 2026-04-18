package com.elegant.software.blitzpay.payments.stripe.config

import com.stripe.Stripe
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct

@Configuration
@EnableConfigurationProperties(StripeProperties::class)
class StripeConfig(private val properties: StripeProperties) {

    @PostConstruct
    fun configureStripe() {
        Stripe.apiKey = properties.secretKey
    }
}
