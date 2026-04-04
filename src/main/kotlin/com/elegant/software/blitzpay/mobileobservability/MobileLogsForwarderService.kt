package com.elegant.software.blitzpay.mobileobservability

import com.elegant.software.blitzpay.mobileobservability.api.MobileContext
import com.elegant.software.blitzpay.mobileobservability.api.MobileEvent
import com.elegant.software.blitzpay.mobileobservability.api.MobileLogsForwarder
import com.elegant.software.blitzpay.mobileobservability.api.MobileLogsRequest
import com.elegant.software.blitzpay.mobileobservability.support.MobileObservabilityProperties
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class MobileLogsForwarderService(
    private val otlpWebClient: WebClient,
    private val props: MobileObservabilityProperties
) : MobileLogsForwarder {

    override fun forward(request: MobileLogsRequest): Int {
        val events = request.events.take(props.maxEventsPerRequest)
        if (events.isEmpty()) return 0

        val ctx = request.context ?: MobileContext()

        val resource = mapOf(
            "attributes" to toOtlpAttrs(
                mapOf(
                    "service.name" to nv(ctx.serviceName, "blitzpay-mobile"),
                    "service.version" to nv(ctx.serviceVersion, "unknown"),
                    "deployment.environment" to nv(ctx.environment, "unknown"),
                    "service.namespace" to nv(props.serviceNamespace, "blitzpay")
                )
            )
        )

        val records = events.map { event -> buildLogRecord(event, ctx) }

        val body = mapOf(
            "resourceLogs" to listOf(
                mapOf(
                    "resource" to resource,
                    "scopeLogs" to listOf(
                        mapOf(
                            "scope" to mapOf("name" to "blitzpay.mobile.observability"),
                            "logRecords" to records
                        )
                    )
                )
            )
        )

        val endpoint = normalizeEndpoint(props.logsEndpoint)

        val spec = otlpWebClient.post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)

        if (!props.authHeader.isNullOrBlank() && !props.authValue.isNullOrBlank()) {
            spec.header(props.authHeader!!, props.authValue!!)
        }

        spec.bodyValue(body)
            .retrieve()
            .toBodilessEntity()
            .doOnError { e -> logger.warn(e) { "Failed to forward mobile logs to $endpoint" } }
            .onErrorResume { Mono.empty() }
            .subscribe()

        return records.size
    }

    private fun buildLogRecord(event: MobileEvent, ctx: MobileContext): Map<String, Any> {
        val attrs = sanitizeAttrs(event.attributes).toMutableMap()
        attrs["session.id"] = nv(ctx.sessionId, "unknown")
        attrs["host.os.name"] = nv(ctx.osName, "unknown")
        attrs["host.os.version"] = nv(ctx.osVersion, "unknown")

        event.exception?.let { ex ->
            ex.type?.takeIf { it.isNotBlank() }?.let { attrs["exception.type"] = sanitize(it) }
            ex.message?.takeIf { it.isNotBlank() }?.let { attrs["exception.message"] = sanitize(it) }
            ex.stack?.takeIf { it.isNotBlank() }?.let { attrs["exception.stacktrace"] = sanitize(it) }
            ex.isFatal?.let { attrs["exception.is_fatal"] = it }
        }

        return mapOf(
            "timeUnixNano" to toUnixNano(event.timestamp),
            "severityText" to nv(event.severityText, "INFO").uppercase(),
            "severityNumber" to severityNumber(event.severityText),
            "body" to mapOf("stringValue" to sanitize(event.message)),
            "attributes" to toOtlpAttrs(attrs)
        )
    }

    companion object {
        private val SENSITIVE_KEY = Regex("(pass(word)?|token|secret|authorization|cookie)", RegexOption.IGNORE_CASE)
        private val EMAIL_PATTERN = Regex("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", RegexOption.IGNORE_CASE)
        private val BEARER_PATTERN = Regex("\\bBearer\\s+[A-Za-z0-9\\-._~+/]+=*", RegexOption.IGNORE_CASE)

        internal fun normalizeEndpoint(raw: String): String {
            val trimmed = raw.trimEnd('/')
            return if (trimmed.endsWith("/v1/logs")) trimmed else "$trimmed/v1/logs"
        }

        internal fun toUnixNano(ts: String?): String = try {
            (Instant.parse(ts).toEpochMilli() * 1_000_000L).toString()
        } catch (_: Exception) {
            (System.currentTimeMillis() * 1_000_000L).toString()
        }

        internal fun severityNumber(severity: String?): Int {
            val v = nv(severity, "INFO").uppercase()
            return when {
                v.startsWith("TRACE") -> 1
                v.startsWith("DEBUG") -> 5
                v.startsWith("INFO") -> 9
                v.startsWith("WARN") -> 13
                v.startsWith("ERROR") -> 17
                v.startsWith("FATAL") -> 21
                else -> 9
            }
        }

        internal fun sanitize(value: String?): String =
            BEARER_PATTERN.replace(
                EMAIL_PATTERN.replace(value ?: "", "[REDACTED_EMAIL]"),
                "Bearer [REDACTED_TOKEN]"
            )

        internal fun sanitizeAttrs(input: Map<String, Any>?): Map<String, Any> {
            if (input == null) return emptyMap()
            return input.entries
                .filter { (k, v) -> k.isNotBlank() && (v is String || v is Number || v is Boolean) }
                .associate { (k, v) ->
                    if (SENSITIVE_KEY.containsMatchIn(k)) k to "[REDACTED]"
                    else k to (if (v is String) sanitize(v) else v)
                }
        }

        private fun toOtlpAttrs(attrs: Map<String, Any>): List<Map<String, Any>> =
            attrs.map { (key, value) -> mapOf("key" to key, "value" to toOtlpValue(value)) }

        private fun toOtlpValue(v: Any): Map<String, Any> = when (v) {
            is Boolean -> mapOf("boolValue" to v)
            is Int, is Long -> mapOf("intValue" to v.toString())
            is Float, is Double -> mapOf("doubleValue" to (v as Number).toDouble())
            else -> mapOf("stringValue" to v.toString())
        }

        private fun nv(value: String?, default: String): String =
            if (!value.isNullOrBlank()) value else default
    }
}
