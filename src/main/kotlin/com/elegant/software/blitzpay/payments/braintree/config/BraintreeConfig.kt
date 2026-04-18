package com.elegant.software.blitzpay.payments.braintree.config

import com.braintreegateway.BraintreeGateway
import com.braintreegateway.Environment
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(BraintreeProperties::class)
class BraintreeConfig {

    @Bean
    @ConditionalOnExpression("!'\${braintree.merchant-id:}'.isEmpty()")
    fun braintreeGateway(properties: BraintreeProperties): BraintreeGateway {
        val env = if (properties.environment.lowercase() == "production") {
            Environment.PRODUCTION
        } else {
            Environment.SANDBOX
        }
        return BraintreeGateway(env, properties.merchantId, properties.publicKey, properties.privateKey)
    }
}
