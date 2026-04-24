# Spring Boot Best Practices

This document is the authoritative reference for Spring Boot coding conventions in this project.
All contributors and AI agents must follow these patterns. `CONTRIBUTING.md` links here for
Spring-specific guidance.

---

## 1. Typed Configuration Properties over `@Value`

**Pattern:** Use `@ConfigurationProperties` with a typed Kotlin data class instead of
scattering `@Value` annotations across beans.

**Why:**
- IDE autocomplete and compile-time safety on property names
- Startup fails fast with a clear binding error if a property is missing or mistyped
- All related config lives in one place — easier to change and test
- Naturally supports nested structures and default values

**Do this:**

```kotlin
// config/ApiVersionProperties.kt
@ConfigurationProperties(prefix = "api")
data class ApiVersionProperties(
    val defaultVersion: String = "1",
    val versions: Versions = Versions()
) {
    data class Versions(
        val invoice: String = "1",
        val qrpay: String = "1",
        val truelayer: String = "1",
        val payments: String = "1"
    )
}
```

Register it once in the application class:

```kotlin
@SpringBootApplication
@EnableConfigurationProperties(ApiVersionProperties::class)
class QuickpayApplication
```

Inject it via constructor:

```kotlin
@Configuration
class InvoiceOpenApiConfig(private val apiVersionProperties: ApiVersionProperties) {
    // access via apiVersionProperties.versions.invoice
}
```

**Not this:**

```kotlin
@Configuration
class InvoiceOpenApiConfig(
    @Value("\${api.versions.invoice}") private val invoiceVersion: String,
    @Value("\${api.versions.truelayer}") private val truelayerVersion: String,
    // ... grows indefinitely
)
```

**Where this is used in the codebase:**
- `config/ApiVersionProperties.kt` — API version config
- `config/WebFluxVersioningConfig.kt` — consumes `defaultVersion`
- `invoice/config/OpenApiConfig.kt`, `payments/qrpay/config/OpenApiConfig.kt`, etc. — consume per-module versions

---

## 2. Constructor Injection

**Pattern:** Always use constructor injection. Never use field injection (`@Autowired` on a field).

**Why:** Constructor injection makes dependencies explicit, enables immutability (`val`),
and makes the class testable without a Spring context.

```kotlin
// Correct — constructor injection, val field
@Configuration
class WebFluxVersioningConfig(
    private val apiVersionProperties: ApiVersionProperties
) : WebFluxConfigurer { ... }

// Wrong — field injection
@Configuration
class WebFluxVersioningConfig {
    @Autowired
    private lateinit var apiVersionProperties: ApiVersionProperties
}
```

---

## 3. Kotlin `data class` for DTOs and Configuration Properties

**Pattern:** Use `data class` for DTOs and `@ConfigurationProperties` holders.
Do not use `data class` for JPA entities.

**Why for DTOs/config:** Automatic `equals`, `hashCode`, `copy`, and `toString` are correct
and safe for value-type objects.

**Why not for JPA entities:** JPA proxies and lazy-loading break `data class` semantics.
Entity identity is based on database ID, not structural equality. Use a regular `class`
with an explicit `id` field instead.

```kotlin
// Correct — data class for config
data class Versions(val invoice: String = "1", val qrpay: String = "1")

// Correct — regular class for JPA entity
@Entity
class PaymentRequest(
    @Id @GeneratedValue val id: UUID = UUID.randomUUID(),
    val amount: BigDecimal,
    ...
)
```

---

## 4. OpenAPI / Swagger — Per-Module Grouping

**Pattern:** Every module that exposes HTTP endpoints defines its own `GroupedOpenApi` bean
in a module-local `OpenApiConfig`. The global `OpenApiConfig` only defines non-module groups
(actuator, general).

**Why:** Keeps module boundaries intact in the API documentation. Each module owns its API spec
in the same way it owns its controllers and services.

**Structure:**

```
config/OpenApiConfig.kt           ← general + actuator groups, rewriteVersionPaths utility
invoice/config/OpenApiConfig.kt   ← Invoice group
payments/qrpay/config/OpenApiConfig.kt  ← QRPay group
payments/truelayer/config/OpenApiConfig.kt ← TrueLayer group
```

**Swagger UI path rewriting:** Because Spring API versioning registers controller paths as
`/{version}/...`, the generated OpenAPI spec contains `{version}` as a literal path variable.
Swagger UI would send that literally, causing 404s. Each `GroupedOpenApi` uses the shared
`rewriteVersionPaths()` utility (in `config/OpenApiExtensions.kt`) to replace `/{version}/`
with the concrete version (e.g., `/v1/`) at spec-generation time.

**Group naming rule:** Springdoc still requires a `group(...)` value in each `GroupedOpenApi` bean,
because that value defines the actual docs endpoint (`/api-docs/{group}`). In this repository, the
source of truth for both group IDs and display labels is YAML-backed configuration properties, not
hardcoded strings inside the config classes.

Use a typed `@ConfigurationProperties` model for OpenAPI groups and have both of these read from it:

- `GroupedOpenApi.group(...)` for the stable docs endpoint ID
- `springdoc.swagger-ui.urls[].name` for the user-facing Swagger UI label

This keeps the definitions in one place while still satisfying Springdoc's requirement that group IDs
exist in code at bean construction time.

See `reference/api-versioning-guide.md` for the full API versioning design.

---

## 5. API Versioning Configuration

**Pattern:** Versions are driven by `application.yml` via `ApiVersionProperties`.
Never hardcode version strings in code.

```yaml
api:
  default-version: 1     # used by WebFlux routing fallback
  versions:
    invoice: 1
    qrpay: 1
    truelayer: 1
    payments: 1
```

To bump a single module to v2: change `api.versions.invoice: 2`. Only that module's
Swagger group and routing change — nothing else.

For the full versioning design (custom resolver, controller mapping pattern, Swagger
path rewriting): see `reference/api-versioning-guide.md`.

---

## 6. Module Metadata in Kotlin

**Pattern:** Declare Spring Modulith module metadata via a dedicated annotated type,
not `package-info.java` (which Kotlin does not support).

```kotlin
@PackageInfo
@ApplicationModule(allowedDependencies = ["invoice"])
package com.elegant.software.blitzpay.payments.qrpay
```

---

## 7. Reactive Stack (WebFlux)

This project is fully reactive (Spring WebFlux / Netty). Do not mix in servlet-stack types.

- Use `WebFluxConfigurer`, not `WebMvcConfigurer`
- Use `ServerWebExchange`, not `HttpServletRequest`
- Use `Mono`/`Flux` return types in reactive chains
- Avoid blocking I/O on the reactive thread pool (`block()`, `Thread.sleep()`, JDBC calls
  outside a scheduler)

---

## References

- Spring Boot Configuration Properties: https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties
- Spring Modulith: https://docs.spring.io/spring-modulith/reference/
- Spring WebFlux API Versioning: https://docs.spring.io/spring-framework/reference/web/webflux-versioning.html
- SpringDoc OpenAPI: https://springdoc.org
