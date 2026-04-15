package com.elegant.software.blitzpay.support

import com.elegant.software.blitzpay.payments.push.config.ExpoPushProperties
import com.elegant.software.blitzpay.payments.push.internal.ExpoMessage
import com.elegant.software.blitzpay.payments.push.internal.ExpoPushClient
import com.elegant.software.blitzpay.payments.push.internal.ExpoTicket
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.web.reactive.function.client.WebClient

@TestConfiguration
@Profile("contract-test")
class ContractTestConfig {

    @Bean
    @Primary
    fun mockExpoPushClient(properties: ExpoPushProperties, expoWebClient: WebClient): ExpoPushClient =
        object : ExpoPushClient(properties, expoWebClient) {
            override fun send(messages: List<ExpoMessage>): List<ExpoTicket> =
                messages.map { ExpoTicket(it.to, "contract-ticket-${it.to.hashCode()}", ExpoTicket.Status.OK, null) }
        }
}
