# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

- Kotlin 2.3.20 on Java 25, Spring Boot 4.0.4, Spring WebFlux (reactive)
- Spring Modulith for module enforcement and cross-module event publishing
- PostgreSQL 16 with Hibernate (`ddl-auto: update`, no migration framework)
- TrueLayer Java SDK for payment gateway integration
- Mustang Project + Flying Saucer for ZUGFeRD/Factur-X invoice generation

## Commands

```bash
./gradlew clean build        # full build including all tests
./gradlew test               # unit tests only
./gradlew contractTest       # API contract suite (WebTestClient, no real DB or TrueLayer)
./gradlew check              # unit tests + contract tests
./gradlew bootRun            # run app (requires env vars below)
```

Run a single test class:
```bash
./gradlew test --tests "com.elegant.software.blitzpay.invoice.InvoiceControllerTest"
```

## Required Environment Variables

```
TRUELAYER_CLIENT_ID
TRUELAYER_CLIENT_SECRET
TRUELAYER_KEY_ID
TRUELAYER_MERCHANT_ACCOUNT_ID
TRUELAYER_PRIVATE_KEY_PATH   # path to PEM private key
```

Contract tests do **not** require these — TrueLayer beans are mocked under the `contract-test` Spring profile.

## Architecture

The app uses **Spring Modulith**: each direct sub-package of `com.elegant.software.blitzpay` is an application module owning its API, internal implementation, persistence, and events.

### Modules

- **`payments.truelayer`** — TrueLayer gateway integration. Inbound: `WebhookController` receives payment status callbacks and verifies TrueLayer signatures. Outbound: `PaymentService` initiates payments. Public surface: `PaymentGateway` interface.
- **`payments.qrpay`** — QR-code payment requests. `PaymentRequestController` creates payment requests; `QrPaymentSseController` streams status updates via SSE. Listens for `PaymentInitRequest` events and publishes results via `PaymentUpdateBus`.
- **`invoice`** — EU-standard ZUGFeRD/Factur-X invoice generation. `InvoiceController` routes by `Accept` header to XML or PDF generation. Public surface: `InvoiceGateway` / `InvoiceService`.

### Cross-module communication

Modules communicate via Spring `ApplicationEventPublisher` / `@ApplicationModuleListener`. Direct bean coupling across modules is an architecture smell — prefer published domain events for async interactions. Named interfaces (`@NamedInterface`) are used to expose sub-package contracts without making entire `internal` packages public.

### API versioning

URL-path versioning (`/v1/...`) uses a custom `PathOnlyApiVersionResolver` that prevents version parsing collisions with Swagger/documentation routes. Config: `useVersionRequired(false)`, `setDefaultVersion("1")`, `detectSupportedVersions(true)`. See `reference/api-versioning-guide.md` for details.

### Module metadata

Because Kotlin lacks `package-info.java`, module metadata is declared via a dedicated type annotated with `@org.springframework.modulith.PackageInfo`. Use this for `@ApplicationModule` and `@NamedInterface` declarations.

## Testing

- **Unit tests** (`src/test/kotlin`) — JUnit 5 + Mockito Kotlin. JSON fixtures in `src/test/resources/testdata/` loaded via `TestFixtureLoader`.
- **Contract tests** (`src/contractTest/kotlin`) — handwritten `WebTestClient` tests. The `contract-test` profile excludes DataSource, JPA, and Modulith event persistence auto-configuration; TrueLayer beans are mocked. Groovy contracts live in `src/contractTest/resources/contracts/`.
- **Module verification** — maintain `ApplicationModules.of(...).verify()` tests. Use `@ApplicationModuleTest` for single-module integration tests.

## Contributor Guide

Full contribution conventions — commit style, architecture principles, Spring Boot / Modulith / JPA / Liquibase best practices, CI/CD setup, and coding convention references — are in **`CONTRIBUTING.md`**.

## Commit Convention

Semantic commits: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`. Summaries: short, imperative, specific. One semantic commit per logical change; squash fixup commits before merging.

## Active Technologies
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux (reactive), Spring Modulith, Spring Data JPA, Hibernate (001-merchant-onboarding)
- PostgreSQL 16 — `ddl-auto: update`, no migration framework. All required tables already exist. (001-merchant-onboarding)

## Recent Changes
- 001-merchant-onboarding: Added Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux (reactive), Spring Modulith, Spring Data JPA, Hibernate
