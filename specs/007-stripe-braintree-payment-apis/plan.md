# Implementation Plan: Stripe & Braintree Payment APIs

**Branch**: `007-stripe-braintree-payment-apis` | **Date**: 2026-04-18 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `specs/007-stripe-braintree-payment-apis/spec.md`

## Summary

Add two new Spring Modulith modules — `payments.stripe` and `payments.braintree` — that expose HTTP endpoints enabling mobile clients to initiate card payments via Stripe and PayPal/digital-wallet payments via Braintree. Both modules are stateless gateway proxies: they accept requests from the mobile app, delegate to the respective payment provider SDK, and return the credentials or transaction outcomes the mobile SDK needs.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on JDK 25  
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux (reactive), Spring Modulith, `stripe-java` SDK, `braintree-java` SDK  
**Storage**: No new tables — both modules are stateless proxies; no persistence required for MVP  
**Testing**: JUnit 5 + Mockito-Kotlin (unit), WebTestClient (contract tests)  
**Target Platform**: JVM server (same deployment as existing app)  
**Project Type**: Web service — reactive HTTP API  
**Performance Goals**: Payment session creation ≤ 3 s p95; Braintree client token ≤ 2 s p95; Braintree checkout ≤ 5 s p95  
**Constraints**: Secret keys must never appear in response bodies or logs; Braintree must degrade gracefully when unconfigured  
**Scale/Scope**: Same as existing app — single-instance deployment with HikariCP connection pool already in place

## Constitution Check

The project constitution file (`constitution.md`) is an unfilled template — no active rules apply. Governance falls back to the architectural conventions in `CLAUDE.md` and `CONTRIBUTING.md`:

| Gate | Status | Notes |
|------|--------|-------|
| Spring Modulith module boundary respected | PASS | Two new `payments.*` modules, no direct cross-module bean coupling |
| No `ddl-auto` schema changes (Liquibase owns schema) | PASS | Both modules are stateless — no new tables |
| SLF4J logging via `LoggerFactory` | PASS | Must NOT use `mu.KotlinLogging` per project feedback |
| Reactive WebFlux style consistent with existing code | PASS | Stripe/Braintree SDKs are blocking; wrap with `Schedulers.boundedElastic()` |
| Contract tests required for new public endpoints | PASS | Three new WebTestClient contract tests needed |
| API versioning via URL-path (`/v1/...`) | PASS | Follow existing `PathOnlyApiVersionResolver` pattern |

**No gate failures. Proceeding.**

## Project Structure

### Documentation (this feature)

```text
specs/007-stripe-braintree-payment-apis/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── stripe-create-intent.json
│   └── braintree.json
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code

```text
src/main/kotlin/com/elegant/software/blitzpay/
├── payments/
│   ├── stripe/                          # NEW module
│   │   ├── package-info.kt              # @ApplicationModule declaration
│   │   ├── api/
│   │   │   ├── package-info.kt          # @NamedInterface
│   │   │   └── StripePaymentGateway.kt  # public interface (optional, for future cross-module use)
│   │   ├── config/
│   │   │   ├── StripeProperties.kt      # STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY
│   │   │   ├── StripeConfig.kt          # @Bean Stripe SDK instance
│   │   │   └── StripeOpenApiConfig.kt
│   │   └── internal/
│   │       ├── StripePaymentController.kt   # POST /v1/payments/stripe/create-intent
│   │       └── StripePaymentService.kt
│   │
│   ├── braintree/                       # NEW module
│   │   ├── package-info.kt              # @ApplicationModule declaration
│   │   ├── api/
│   │   │   └── package-info.kt          # @NamedInterface
│   │   ├── config/
│   │   │   ├── BraintreeProperties.kt   # BRAINTREE_MERCHANT_ID, PUBLIC_KEY, PRIVATE_KEY, ENV
│   │   │   ├── BraintreeConfig.kt       # @Bean BraintreeGateway (nullable/optional)
│   │   │   └── BraintreeOpenApiConfig.kt
│   │   └── internal/
│   │       ├── BraintreePaymentController.kt  # POST /v1/payments/braintree/client-token
│   │       │                                  # POST /v1/payments/braintree/checkout
│   │       └── BraintreePaymentService.kt

src/contractTest/kotlin/com/elegant/software/blitzpay/
├── payments/
│   ├── stripe/
│   │   └── StripePaymentControllerContractTest.kt
│   └── braintree/
│       └── BraintreePaymentControllerContractTest.kt

src/test/kotlin/com/elegant/software/blitzpay/
├── payments/
│   ├── stripe/
│   │   └── StripePaymentServiceTest.kt
│   └── braintree/
│       └── BraintreePaymentServiceTest.kt
```

**Structure Decision**: Two sibling modules `payments.stripe` and `payments.braintree` under the existing `payments` parent package, mirroring `payments.truelayer`. Both are stateless; no persistence sub-package needed. Blocking SDK calls wrapped in `Schedulers.boundedElastic()` to stay WebFlux-compliant.

## Complexity Tracking

No constitution violations — table omitted.

---

## Phase 0: Research

See [research.md](research.md).

---

## Phase 1: Design

See [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md).
