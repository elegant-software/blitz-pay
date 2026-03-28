package com.elegant.software.blitzpay.config

import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebFluxVersioningConfigTest {

    private val resolver = PathOnlyApiVersionResolver()

    @Test
    fun `extracts version from v-prefixed api path`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v1/invoices").build())

        assertEquals("1", resolver.resolveVersion(exchange))
    }

    @Test
    fun `supports semantic versions in api path`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v1.2/payments/request").build())

        assertEquals("1.2", resolver.resolveVersion(exchange))
    }

    @Test
    fun `ignores non api paths`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/swagger-ui/index.html").build())

        assertNull(resolver.resolveVersion(exchange))
    }
}
