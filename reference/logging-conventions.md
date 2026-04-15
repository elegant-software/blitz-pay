# Logging Conventions

Authoritative reference for how blitz-pay emits and structures logs. All modules must follow these rules. Production log ingestion (Loki / Elastic / Cloud Logging) relies on the structured JSON shape — ad-hoc `println`, `KotlinLogging`, or free-text log lines break downstream tooling.

See `CONTRIBUTING.md` and `CONSTITUTION.md` → *Observability and Logging* for the binding rules; this document is the how-to.

---

## 1. Facade: SLF4J, not KotlinLogging

**Rule:** Obtain loggers via `org.slf4j.LoggerFactory`. Do not introduce `mu.KotlinLogging` or `io.github.microutils:kotlin-logging-jvm` in new code.

```kotlin
import org.slf4j.LoggerFactory

class PaymentStatusService(...) {
    private val log = LoggerFactory.getLogger(javaClass)
    // ...
}
```

Why SLF4J directly:
- The `logstash-logback-encoder` pipeline and MDC integration are designed for SLF4J events. KotlinLogging wraps SLF4J but hides the `{}` parameterised form from our JSON encoder, which costs us both structured fields and stack-trace fidelity.
- Keeping one facade means one mental model for every contributor and one set of matchers in our log queries.
- Parameterised SLF4J calls are as terse as the Kotlin lambda form once you adopt the `key=value` style below.

Legacy usages of `KotlinLogging` exist in some modules and will be migrated on touch. Do not add new ones.

---

## 2. Structured JSON output

Production, staging, and any non-local profile emit one JSON object per log event via `logback-spring.xml` (`LoggingEventCompositeJsonEncoder`). Local and contract-test profiles emit a human-readable colourised pattern.

Standard JSON fields:
- `@timestamp` — UTC ISO-8601
- `level`, `logger`, `thread_name`, `message`
- `service` — from `spring.application.name`
- `env` — from `SPRING_PROFILES_ACTIVE`
- All MDC keys flattened as top-level fields
- `stack_trace` — shortened, root-cause-first

Do not introduce parallel appenders. Add a new Logback profile in `logback-spring.xml` if a new environment needs a different shape; do not hand-roll JSON in log messages.

---

## 3. MDC and `LogContext`

Per-request / per-operation correlation data lives in SLF4J's MDC so every line inside a scope inherits it automatically. Use `com.elegant.software.blitzpay.config.LogContext.with(...)` instead of touching `MDC` directly — it restores prior values on exit and handles nulls.

```kotlin
LogContext.with(
    LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
    LogContext.EVENT_ID to eventId,
) {
    log.info("payment status updated previous={} new={}", previous, new)
}
```

Canonical MDC keys (defined as constants on `LogContext`):
| Key                 | Meaning                                                              |
|---------------------|----------------------------------------------------------------------|
| `traceId`           | Per-request correlation id, set by `TraceIdWebFilter`                |
| `paymentRequestId`  | Payment request UUID on webhook / status / push paths                |
| `eventId`           | TrueLayer event id being processed                                   |
| `webhookId`         | TrueLayer `x-tl-webhook-id` header                                   |
| `orderId`           | Merchant order reference                                             |

Add a new key only when it names a long-lived identifier that spans multiple log lines. One-shot fields belong in the log message arguments, not MDC.

### Reactive chains

MDC is thread-local; in fully reactive `Mono` / `Flux` chains it can be lost across schedulers. Use `Mono.deferContextual { ctx -> ... }` and read the `traceId` from the Reactor `Context` populated by `TraceIdWebFilter`. In virtual-thread / blocking handlers, MDC Just Works.

---

## 4. Trace id propagation

`TraceIdWebFilter` (highest-precedence `WebFilter`) assigns each inbound request a `traceId`, falling back to the value of an incoming `X-Request-Id` or `x-tl-webhook-id` header so upstream correlation ids survive. It:
- Puts `traceId` in MDC (so every log line carries it)
- Writes it into the Reactor `Context` under key `traceId`
- Sets the `X-Request-Id` response header

Outbound HTTP calls that fan out to other services should forward `traceId` as `X-Request-Id` on the outgoing request.

---

## 5. Message format — structured key=value

Log messages are consumed by humans AND queries. Prefer short, parameterised messages with `key=value` tokens for every variable piece of data. This makes lines greppable in dev and parseable in Loki/Elastic without regex gymnastics.

**Do:**
```kotlin
log.info("webhook accepted eventId={} type={} paymentRequestId={}", eventId, type, paymentRequestId)
log.warn("webhook rejected reason=signature_invalid webhookId={}", webhookId)
```

**Don't:**
```kotlin
// Free-text is unparseable and often leaks concatenated payloads:
log.info("Webhook $rawBody accepted by $service at ${Instant.now()}")
// Also don't pre-format with string templates — it defeats SLF4J's
// lazy evaluation and loses structured arguments:
log.info("eventId=$eventId type=$type")
```

Rules:
- Always use SLF4J's `{}` placeholders, not Kotlin string templates.
- For each variable, emit a dedicated `key=value` token in the message and pass the value as a placeholder argument.
- The first few tokens after the verb should be a stable *reason* / *outcome* (`reason=signature_invalid`, `outcome=accepted`, `status=SETTLED`) so filtering by outcome is cheap.
- Pass exceptions as the last argument to `log.warn`/`log.error` — do not `.toString()` them into the message.

```kotlin
log.error("jwks fetch failed webhookId={}", webhookId, ex)
```

---

## 6. Log levels

| Level  | When to use                                                                 |
|--------|-----------------------------------------------------------------------------|
| ERROR  | Something went wrong that needs human attention: upstream outage, data corruption, unhandled exception reaching a boundary. Always include the exception. |
| WARN   | Expected-but-unhappy paths that we want visibility on: rejected webhook signature, invalid Expo token, retry exhausted. |
| INFO   | Business-relevant lifecycle events: payment accepted, push dispatched, status transition. One per operation, not per step. |
| DEBUG  | Developer-diagnostic detail: raw payloads, internal state transitions. Off by default; opted in via `LOGGING_LEVEL_...` env vars. |
| TRACE  | Avoid. If you need it, you probably need a metric or a span instead.        |

Rules:
- Do not log at `INFO` inside tight loops.
- Never log at `ERROR` for a recoverable, retried condition.
- Default root level is `INFO`; package-level overrides go in `logback-spring.xml`, not in code.

---

## 7. What not to log

- **Secrets:** API keys, access tokens, Expo tokens in full, TrueLayer client secrets, private keys. Mask tokens to the first 8 / last 4 chars (see `PushDispatcher.maskToken`).
- **PII:** full payer names, account numbers, emails. Log the `paymentRequestId` and look the rest up in the DB when debugging.
- **Full request / response bodies** at `INFO`. If you need them to diagnose a problem, gate them behind `DEBUG` and a short-lived logger override.
- **Stack traces pre-rendered as strings.** Pass the `Throwable` as the last argument; the encoder shortens and formats it.

---

## 8. Configuration surface

All log-related configuration lives in one of:
- `src/main/resources/logback-spring.xml` — profiles, encoders, root and package levels
- `application.yml` — no logging config by default; use `LOGGING_LEVEL_{PACKAGE}` env vars to override levels at deploy time
- `TraceIdWebFilter` — request-scope MDC plumbing
- `LogContext` — operation-scope MDC plumbing

Do not introduce per-module Logback XML files; do not set `logging.level.*` under `spring:` in `application.yml`. Keep the log config in one place.

---

## References

- SLF4J user manual: https://www.slf4j.org/manual.html
- Logback manual: https://logback.qos.ch/manual/
- logstash-logback-encoder: https://github.com/logfellow/logstash-logback-encoder
