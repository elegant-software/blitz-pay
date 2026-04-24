# API Versioning Guide

## Goal

This project uses Spring Framework 7 / Spring Boot 4 API versioning for application endpoints whose URL path starts with a version segment such as `/v1/...`.

The design goal is:

- Use Spring's native API versioning support for API routes
- Keep version information in the request path
- Avoid breaking Swagger UI and OpenAPI endpoints

## Why The Default Path Resolver Was Not Enough

Spring's built-in `usePathSegment(0)` configuration treats the first path segment of every request as an API version candidate.

That works for routes like:

- `/v1/invoices`
- `/v1/payments/request`

But it also affects non-API routes such as:

- `/swagger-ui.html`
- `/swagger-ui/index.html`
- `/api-docs`
- `/api-docs/swagger-config`

If a documentation endpoint begins with something that looks like a version, or if Spring expects every request to participate in version resolution, Swagger can fail with `400 Bad Request` or disappear from routing.

## Current Solution

The project uses a custom resolver in [WebFluxVersioningConfig.kt](/Users/mehdi/MyProject/BlitzPay/src/main/kotlin/com/elegant/software/blitzpay/config/WebFluxVersioningConfig.kt).

The resolver behavior is:

- Read the first real path segment
- If it matches `v1`, `v2`, `v1.2`, and similar forms, extract the numeric version only
- Return `null` for all other paths

Examples:

- `/v1/invoices` -> `1`
- `/v1.2/payments/request` -> `1.2`
- `/swagger-ui/index.html` -> `null`
- `/api-docs/swagger-config` -> `null`

This allows Spring API versioning to apply only to versioned API paths, while Swagger and other infrastructure endpoints stay outside that concern.

## Spring Configuration

Current configuration:

- `useVersionResolver(PathOnlyApiVersionResolver())`
- `setVersionRequired(false)`
- `setDefaultVersion("1")`
- `detectSupportedVersions(true)`

Why each setting matters:

- `useVersionResolver(...)`: limits version extraction to paths that actually start with `v...`
- `setVersionRequired(false)`: allows non-API paths to resolve without an explicit version
- `setDefaultVersion("1")`: gives Spring a stable fallback for non-versioned infrastructure routes
- `detectSupportedVersions(true)`: lets Spring collect supported versions from controller mappings

## Controller Mapping Pattern

Controllers use a versioned path variable together with Spring's `version` attribute.

Example:

```kotlin
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/invoices", version = "1")
```

This means:

- The URL must begin with a `v...` path segment
- Spring parses that path segment as the API version
- The controller mapping is explicitly bound to API version `1`

This keeps Spring's native versioning feature active instead of treating `/v1` as a plain string prefix only.

## Swagger / Springdoc Configuration

Swagger stays on a non-versioned base path:

```yaml
springdoc:
  api-docs:
    path: /api-docs
```

Grouped Swagger URLs also point to `/api-docs/...` rather than `/v3/api-docs/...`.

This is intentional. Reverting to the default `/v3/api-docs` would reintroduce a collision with path-based API version parsing because the first segment would again look like a version.

For grouped docs, note the separation of responsibilities:

- `GroupedOpenApi.group("...")` defines the actual group identifier and therefore the docs URL
  such as `/api-docs/Invoice`
- `springdoc.swagger-ui.urls[].name` defines the display label shown in the Swagger UI topbar

With plain Springdoc, removing the YAML entries causes Swagger UI to display the raw
`GroupedOpenApi.group(...)` values. To avoid duplicating names, this repository centralizes both the
group IDs and labels in YAML-backed configuration properties, and the `GroupedOpenApi` beans read
their `group(...)` values from that shared source.

## Tradeoff

One side effect is that generated OpenAPI paths may appear as `/{version}/...` instead of a literal `/v1/...` path.

That is a documentation rendering concern, not a routing problem. Runtime routing and Swagger loading both work with the current setup.

If needed later, that can be refined with springdoc customization.

## Tests

Resolver behavior is covered by:

- [WebFluxVersioningConfigTest.kt](/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/config/WebFluxVersioningConfigTest.kt)

The test verifies:

- version extraction for `/v1/...`
- semantic version extraction for `/v1.2/...`
- non-interference with `/swagger-ui/...`

## Related

- `reference/spring-boot-best-practices.md` — typed `@ConfigurationProperties` pattern used for version config, per-module OpenAPI grouping, Swagger path rewriting

## References

- Spring Framework WebFlux API Versioning:
  https://docs.spring.io/spring-framework/reference/web/webflux-versioning.html
- Spring Framework WebFlux Config:
  https://docs.spring.io/spring-framework/reference/web/webflux/config.html
