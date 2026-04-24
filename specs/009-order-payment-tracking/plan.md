# Implementation Plan: Order Payment Tracking

**Branch**: `[009-order-payment-tracking]` | **Date**: 2026-04-24 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/009-order-payment-tracking/spec.md`

## Summary

Introduce a dedicated `order` business module that accepts orders built from merchant products, persists an order lifecycle with payment-oriented status, and standardizes the system-generated order ID as the shared payment reference across payment providers. The design keeps merchant catalog ownership in the `merchant` module, keeps payment execution in the `payments` module, and lets the new `order` module own order persistence, status transitions, and payment-attempt linkage.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25  
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Spring Data JPA/Hibernate, Liquibase, Jackson Kotlin module, Bean Validation  
**Storage**: PostgreSQL 16 in schema `blitzpay`  
**Testing**: JUnit 5, Mockito Kotlin, WebTestClient contract tests, Spring Modulith verification tests  
**Target Platform**: Linux server on JVM  
**Project Type**: REST web-service with Spring Modulith business modules  
**Performance Goals**: 95% of valid order submissions complete in under 3 seconds; status lookup stays under 500ms for normal order volumes  
**Constraints**: `ddl-auto` remains `none`/`validate`; Liquibase owns schema; all public HTTP changes require contract tests; cross-module collaboration stays on dedicated API surfaces and published events  
**Scale/Scope**: Hundreds of merchants, thousands of products, low-to-moderate order volume in v1, multiple payment providers reusing the same order ID reference

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Rule | Status | Notes |
|------|--------|-------|
| Kotlin-only application code | ✅ | No Java source planned |
| Spring Modulith boundaries respected | ✅ | New top-level `order` module owns its own tables and listens to payment events |
| API versioning and stable contracts | ✅ | New order endpoints stay under `/v1/...`; existing payment contract changes get contract coverage |
| Liquibase owns schema | ✅ | New order tables and indexes are introduced via Liquibase only |
| Table prefix matches owning leaf module | ✅ | Tables will use `order_` prefixes |
| Tests for every behavior change | ✅ | Unit, contract, and modulith verification updates are part of the plan |

## Project Structure

### Documentation (this feature)

```text
specs/009-order-payment-tracking/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── create-order.json
│   ├── get-order.json
│   └── payment-order-reference.json
└── tasks.md
```

### Source Code (repository root)

```text
src/main/kotlin/com/elegant/software/blitzpay/
├── order/
│   ├── api/
│   ├── application/
│   ├── domain/
│   ├── repository/
│   ├── support/
│   └── web/
├── merchant/
│   └── api/
├── payments/
│   ├── braintree/internal/
│   ├── push/api/
│   ├── qrpay/
│   └── truelayer/api/
└── config/

src/main/resources/
└── db/changelog/

src/test/kotlin/com/elegant/software/blitzpay/
├── order/
├── payments/
└── ModularityTest.kt

src/contractTest/kotlin/com/elegant/software/blitzpay/
├── order/
└── payments/
```

**Structure Decision**: Use a new `order` leaf module because order lifecycle ownership is distinct from merchant catalog ownership and payment execution. The module will depend on `merchant.api` for product validation and will consume `payments.push.api.PaymentStatusChanged` events for status progression.

## Complexity Tracking

No constitution violations are currently required.
