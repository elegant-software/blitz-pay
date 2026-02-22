package com.elegant.software.blitzpay

import com.elegant.software.blitzpay.payments.QuickpayApplication
import org.springframework.boot.fromApplication
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.with
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Test application that provides Arconia-style dev services for local development and testing.
 *
 * This configuration follows the Arconia pattern from https://www.thomasvitale.com/arconia-dev-services-spring-boot/
 * by providing pre-configured Testcontainers with Spring Boot's @ServiceConnection annotation.
 *
 * Run this class to start the application with dev services enabled.
 * The PostgreSQL container will be automatically configured and connected.
 */
fun main(args: Array<String>) {
    fromApplication<QuickpayApplication>()
        .with(DevServicesConfiguration::class)
        .run(*args)
}

/**
 * Configuration class that defines development services using Testcontainers.
 * Inspired by Arconia's approach to dev services.
 */
@Configuration(proxyBeanMethods = false)
class DevServicesConfiguration {

    /**
     * PostgreSQL container configured as a dev service.
     * The @ServiceConnection annotation automatically configures Spring Boot's DataSource
     * to connect to this container.
     *
     * Note: Container reuse requires testcontainers.reuse.enable=true in ~/.testcontainers.properties
     */
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer<*> {
        return PostgreSQLContainer(DockerImageName.parse("postgres:16.2"))
            .withReuse(true)  // Reuse container across multiple test runs for faster startup
    }
}
