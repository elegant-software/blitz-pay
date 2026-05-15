# Implementation Plan: Deferred Payment Order APIs

**Branch**: `018-deferred-payment-order-apis` | **Date**: 2026-05-15 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/018-deferred-payment-order-apis/spec.md`

## Summary

Extend the order and merchant modules to support the full deferred-payment lifecycle: expose DEFERRED_PAYMENT capability in shopper-facing merchant context; allow unpaid-order mutations (add/remove items, quantity changes, explicit cancellation, merchant price override); add manual settlement with `MANUAL_SETTLEMENT` payment source; and enrich order reads with `paymentSource` for both shopper and merchant clients.

The foundational schema columns (`uses_deferred_payment`, `order_type`, `merchant_offerings`, `merchant_application_offerings`) were laid in migration `20260514-001`. Spec 018 builds the lifecycle on top: new Liquibase columns (`payment_source`, `settlement_note`, `merchant_price_override_minor`), new service methods, new endpoints, and corresponding contract tests.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25  
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux (reactive), Spring Modulith 2.1.0-M3, Hibernate/JPA, Liquibase  
**Storage**: PostgreSQL 16 (`blitzpay` schema), `ddl-auto: none` — all schema changes via Liquibase  
**Testing**: JUnit 5 + Mockito Kotlin (unit), WebTestClient contract tests (`src/contractTest/`)  
**Target Platform**: Linux server (Spring Boot app)  
**Project Type**: Web service / REST API  
**Performance Goals**: Standard Spring WebFlux reactive throughput — no specific numeric goal for this feature  
**Constraints**: All schema changes via Liquibase; no `ddl-auto: update`; modules communicate via domain events, not direct cross-module bean injection; table names prefixed with leaf module name (`order_`)  
**Scale/Scope**: Existing `order` Spring Modulith module extended with new endpoints and service methods

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Rule | Status | Notes |
|------|--------|-------|
| Kotlin only, no Java source | PASS | All new code Kotlin |
| Spring Modulith module boundaries | PASS | All order changes stay in `order` module; reads merchant data via `MerchantGateway` / `MerchantOfferingAssignmentRepository` (already cross-module) |
| Cross-module via domain events / `@NamedInterface` | PASS | Only extends existing `order` module; merchant data access follows existing pattern |
| URL-path versioning `/v1/...` | PASS | All new endpoints under `/v1/` |
| Contract tests for every new/changed endpoint | GATE | Must add contract tests for every new endpoint (mutations, settlement, context) |
| Liquibase owns all DDL | PASS | New columns via new Liquibase changesets only |
| Table names prefixed with module name | PASS | All order tables prefixed `order_`; merchant tables prefixed `merchant_` |
| Tests must pass before committing | GATE | `./gradlew check` must be green before each commit |
| Domain language (CONTEXT.md vocabulary) | PASS | Using `usesDeferredPayment`, `CheckoutIntent`, `ManualSettlement`, `PaymentSource` per spec |

No constitution violations. No complexity justification required.

## Project Structure

### Documentation (this feature)

```text
specs/018-deferred-payment-order-apis/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── shopper-order-mutations.md
│   ├── merchant-settlement.md
│   └── merchant-context-deferred.md
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
src/main/kotlin/com/elegant/software/blitzpay/
├── merchant/
│   └── api/
│       └── ProximityModels.kt              # Add deferredPaymentAvailable to MerchantContext
│   └── application/
│       └── ProximityService.kt             # Populate deferredPaymentAvailable
├── order/
│   ├── api/
│   │   └── OrderModels.kt                  # Add PaymentSource, SettleOrderRequest, mutation requests, extended responses
│   ├── domain/
│   │   ├── Order.kt                        # Add paymentSource, settlementNote; add manualSettle(), mutateItems()
│   │   ├── OrderItem.kt                    # Add merchantPriceOverrideMinor, overrideBy, overriddenAt
│   │   └── OrderStatus.kt                  # No changes needed (CREATED/FAILED distinction already sufficient)
│   ├── application/
│   │   └── OrderService.kt                 # Add mutateItems(), settle(), priceOverride(), cancelOrder()
│   └── web/
│       ├── ShopperOrderController.kt        # Add PATCH items, DELETE item, POST cancel
│       └── MerchantOrderController.kt       # Add POST settle, PATCH line price, GET order by orderId

src/main/resources/db/changelog/
├── 20260515-001-order-payment-source.sql    # Add payment_source, settlement_note to order_orders
└── 20260515-002-order-item-price-override.sql # Add merchant_price_override_minor, override_by, overridden_at to order_items

src/contractTest/kotlin/com/elegant/software/blitzpay/
├── order/
│   └── OrderContractTest.kt                # Add deferred-payment, mutation, settlement contract tests
└── merchant/
    └── ProximityContractTest.kt            # Add deferredPaymentAvailable assertion
```

**Structure Decision**: Single project — this is a backend-only Spring Boot service. All changes are within the existing `order` and `merchant` modules. No new modules required.

## Complexity Tracking

No constitution violations requiring justification.
