package com.elegant.software.blitzpay.mobileobservability

import com.elegant.software.blitzpay.mobileobservability.MobileLogsForwarderService.Companion.normalizeEndpoint
import com.elegant.software.blitzpay.mobileobservability.MobileLogsForwarderService.Companion.sanitize
import com.elegant.software.blitzpay.mobileobservability.MobileLogsForwarderService.Companion.sanitizeAttrs
import com.elegant.software.blitzpay.mobileobservability.MobileLogsForwarderService.Companion.severityNumber
import com.elegant.software.blitzpay.mobileobservability.MobileLogsForwarderService.Companion.toUnixNano
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MobileLogsForwarderServiceTest {

    @Nested
    inner class NormalizeEndpoint {
        @Test
        fun `appends v1 logs when missing`() {
            assertEquals("http://loki:3100/otlp/v1/logs", normalizeEndpoint("http://loki:3100/otlp"))
        }

        @Test
        fun `strips trailing slash before appending`() {
            assertEquals("http://loki:3100/otlp/v1/logs", normalizeEndpoint("http://loki:3100/otlp/"))
        }

        @Test
        fun `leaves endpoint unchanged when already correct`() {
            assertEquals("http://loki:3100/otlp/v1/logs", normalizeEndpoint("http://loki:3100/otlp/v1/logs"))
        }
    }

    @Nested
    inner class SeverityMapping {
        @Test
        fun `maps standard severity levels`() {
            assertEquals(1, severityNumber("TRACE"))
            assertEquals(5, severityNumber("DEBUG"))
            assertEquals(9, severityNumber("INFO"))
            assertEquals(13, severityNumber("WARN"))
            assertEquals(17, severityNumber("ERROR"))
            assertEquals(21, severityNumber("FATAL"))
        }

        @Test
        fun `defaults to INFO for null or unknown`() {
            assertEquals(9, severityNumber(null))
            assertEquals(9, severityNumber("UNKNOWN"))
        }
    }

    @Nested
    inner class Sanitization {
        @Test
        fun `redacts email addresses`() {
            val result = sanitize("Contact user@example.com for help")
            assertTrue(result.contains("[REDACTED_EMAIL]"))
            assertFalse(result.contains("user@example.com"))
        }

        @Test
        fun `redacts bearer tokens`() {
            val result = sanitize("Auth: Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig")
            assertTrue(result.contains("Bearer [REDACTED_TOKEN]"))
            assertFalse(result.contains("eyJhbGciOiJSUzI1NiJ9"))
        }

        @Test
        fun `handles null input`() {
            assertEquals("", sanitize(null))
        }
    }

    @Nested
    inner class SanitizeAttributes {
        @Test
        fun `redacts sensitive keys`() {
            val attrs = sanitizeAttrs(mapOf("password" to "secret123", "screen" to "home"))
            assertEquals("[REDACTED]", attrs["password"])
            assertEquals("home", attrs["screen"])
        }

        @Test
        fun `filters out non-primitive values`() {
            val attrs = sanitizeAttrs(mapOf("nested" to mapOf("a" to 1), "valid" to "ok"))
            assertFalse(attrs.containsKey("nested"))
            assertEquals("ok", attrs["valid"])
        }

        @Test
        fun `handles null input`() {
            assertEquals(emptyMap<String, Any>(), sanitizeAttrs(null))
        }
    }

    @Nested
    inner class TimestampConversion {
        @Test
        fun `converts ISO timestamp to unix nanos`() {
            val nanos = toUnixNano("2024-01-15T10:30:00Z")
            assertEquals((1705314600000L * 1_000_000L).toString(), nanos)
        }

        @Test
        fun `falls back to current time for invalid timestamp`() {
            val nanos = toUnixNano("not-a-timestamp")
            assertTrue(nanos.toLong() > 0)
        }
    }
}
