package com.elegant.software.blitzpay

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test to verify that Arconia-style dev services are properly configured.
 *
 * This test uses the DevServicesConfiguration to start a PostgreSQL container
 * and verifies that the Spring Boot application context can be loaded with it.
 */
@SpringBootTest(
    classes = [DevServicesConfiguration::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = [
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.hikari.maximum-pool-size=1"
])
class ArconiaDevServicesIntegrationTest {

    @Autowired(required = false)
    private var postgresContainer: PostgreSQLContainer<*>? = null

    @Test
    fun `should configure PostgreSQL dev service container`() {
        assertNotNull(postgresContainer, "PostgreSQL container should be configured as a dev service")
        assertTrue(
            postgresContainer!!.isRunning,
            "PostgreSQL container should be running"
        )
        println("PostgreSQL container is running at: ${postgresContainer!!.jdbcUrl}")
    }
}
