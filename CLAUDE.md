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
TRUELAYER_PRIVATE_KEY_PATH          # path to PEM private key
EXPO_ACCESS_TOKEN                   # Expo Push access token (payments.push module)
STRIPE_SECRET_KEY                   # Stripe secret key (payments.stripe module)
EXPO_PUBLIC_STRIPE_PUBLISHABLE_KEY  # Stripe publishable key (returned to mobile clients)
BRAINTREE_MERCHANT_ID               # Braintree merchant ID (payments.braintree module)
BRAINTREE_PUBLIC_KEY                # Braintree public key
BRAINTREE_PRIVATE_KEY               # Braintree private key
BRAINTREE_ENVIRONMENT               # sandbox or production (default: sandbox)
```

Contract tests do **not** require these — TrueLayer, Stripe, and Braintree beans are mocked under the `contract-test` Spring profile.

## Architecture

The app uses **Spring Modulith**: each direct sub-package of `com.elegant.software.blitzpay` is an application module owning its API, internal implementation, persistence, and events.

### Modules

- **`payments.truelayer`** — TrueLayer gateway integration. Inbound: `WebhookController` receives payment status callbacks and verifies TrueLayer signatures. Outbound: `PaymentService` initiates payments. Public surface: `PaymentGateway` interface.
- **`payments.qrpay`** — QR-code payment requests. `PaymentRequestController` creates payment requests; `QrPaymentSseController` streams status updates via SSE. Listens for `PaymentInitRequest` events and publishes results via `PaymentUpdateBus`.
- **`payments.stripe`** — Stripe card payment gateway. `StripePaymentController` exposes `POST /v1/payments/stripe/create-intent`; returns `client_secret` and `publishableKey` for mobile Stripe SDK. Stateless — no DB tables.
- **`payments.braintree`** — Braintree PayPal/digital wallet gateway. `BraintreePaymentController` exposes `POST /v1/payments/braintree/client-token` and `POST /v1/payments/braintree/checkout`. Degrades to HTTP 503 when `BRAINTREE_MERCHANT_ID` is absent. Stateless — no DB tables.
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
- Kotlin 2.3.20 on Java 25 (unchanged) + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Hibernate/JPA on PostgreSQL 16, TrueLayer Java SDK (unchanged). New: Reactor `WebClient` against the Expo Push HTTPS API (`https://exp.host/--/api/v2/push/send`) — no additional SDK; a thin in-repo client keeps the dependency surface minimal. (006-push-notifications)
- PostgreSQL via JPA. Schema owned by Liquibase per `CONSTITUTION.md` (Persistence and Schema); Hibernate `ddl-auto` must be `validate` or `none`. Table names use the leaf-module prefix (e.g. `push_device_registration`, `push_payment_status`, `push_delivery_attempt`, `push_processed_webhook_event`). (006-push-notifications)
- Kotlin 2.3.20 on JDK 25 + Spring Boot 4.0.4, Spring WebFlux (reactive), Spring Modulith, `stripe-java` SDK, `braintree-java` SDK (006-push-notifications)
- No new tables — both modules are stateless proxies; no persistence required for MVP (006-push-notifications)

## Recent Changes
- 006-push-notifications: Added Kotlin 2.3.20 on Java 25 (unchanged) + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Hibernate/JPA on PostgreSQL 16, TrueLayer Java SDK (unchanged). New: Reactor `WebClient` against the Expo Push HTTPS API (`https://exp.host/--/api/v2/push/send`) — no additional SDK; a thin in-repo client keeps the dependency surface minimal.
