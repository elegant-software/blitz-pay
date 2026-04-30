package com.elegant.software.blitzpay.config

import org.springframework.context.annotation.Configuration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackages = ["com.elegant.software.blitzpay"])
@EntityScan(basePackages = ["com.elegant.software.blitzpay", "org.springframework.modulith.events.jpa"])
class JpaConfig
