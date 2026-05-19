package com.elegant.software.blitzpay.betterprice.agent.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MarketSearchAgentProperties::class)
class MarketSearchAgentConfiguration
