package com.elegant.software.blitzpay.invoiceagent.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(InvoiceAgentProperties::class)
class InvoiceAgentConfiguration
