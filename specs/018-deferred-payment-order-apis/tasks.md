# Tasks: Deferred Payment Order APIs (018)

**Input**: Design documents from `/specs/018-deferred-payment-order-apis/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓

**Organization**: Tasks grouped by user story — each story is independently testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: User story (US1–US5 maps to spec.md user stories)

---

## Phase 1: Setup (Schema Migrations)

**Purpose**: Lay the DB columns that all user stories depend on.

- [x] T001 Create Liquibase changeset `src/main/resources/db/changelog/20260515-001-order-payment-source.sql` — add `payment_source VARCHAR(32) NULL` and `settlement_note VARCHAR(2000) NULL` to `blitzpay.order_orders`
- [x] T002 [P] Create Liquibase changeset `src/main/resources/db/changelog/20260515-002-order-item-price-override.sql` — add `merchant_price_override_minor BIGINT NULL`, `overridden_by VARCHAR(255) NULL`, `overridden_at TIMESTAMPTZ NULL` to `blitzpay.order_items`
- [x] T003 Register both changesets in `src/main/resources/db/changelog/db.changelog-master.yaml` in date order

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Entity and model changes required before any service or endpoint work.

- [x] T004 Extend `src/main/kotlin/com/elegant/software/blitzpay/order/persistence/model/Order.kt` — add `paymentSource: String?` and `settlementNote: String?` fields; add `manualSettle(note, by, at)` method; extend `applyPaymentUpdate()` to set `paymentSource = "APP_PAYMENT"` on PAID transition
- [x] T005 [P] Extend `src/main/kotlin/com/elegant/software/blitzpay/order/persistence/model/OrderItem.kt` — add `merchantPriceOverrideMinor: Long?`, `overriddenBy: String?`, `overriddenAt: Instant?` fields; add `effectiveUnitPriceMinor` computed property; add `applyPriceOverride(priceMinor, by, at)` method
- [x] T006 [P] Add `PaymentSource` enum, `SettleOrderRequest`, `AddOrderItemRequest`, `UpdateOrderItemRequest`, `MerchantItemPriceOverrideRequest` to `src/main/kotlin/com/elegant/software/blitzpay/order/api/OrderModels.kt`
- [x] T007 Add `paymentSource: PaymentSource?` to `OrderResponse` and `OrderSummaryResponse` in `src/main/kotlin/com/elegant/software/blitzpay/order/api/OrderModels.kt`; update `toResponse()` and `toSummaryResponse()` mapper functions to populate it from `order.paymentSource`

**Checkpoint**: Entity/model layer complete — service and endpoint work can begin.

---

## Phase 3: User Story 1 — Show Deferred Payment Choice From Merchant Capability (P1)

**Goal**: Shopper-facing proximity response includes `deferredPaymentAvailable` reflecting merchant offering.

**Independent Test**: `POST /v1/proximity` for a merchant with DEFERRED_PAYMENT offering returns `merchant.deferredPaymentAvailable=true`; same request for merchant without offering returns `false`.

- [x] T008 [US1] Add `deferredPaymentAvailable: Boolean = false` field to `MerchantContext` data class in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/ProximityModels.kt`
- [x] T009 [US1] Inject `MerchantOfferingAssignmentRepository` into the service that builds `MerchantContext` (locate in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/ProximityService.kt`); query enabled offerings for the merchant and set `deferredPaymentAvailable = "DEFERRED_PAYMENT" in enabledCodes`
- [x] T010 [US1] Add contract test in `src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/` — verify `merchant.deferredPaymentAvailable=true` when merchant has DEFERRED_PAYMENT offering; verify `false` when offering absent

---

## Phase 4: User Story 2 — Create Orders For Pay Now And Pay Later (P1)

**Goal**: Order creation with `usesDeferredPayment=true` creates order at CREATED status with no payment started and `paymentSource=null`; existing Pay now flow unaffected.

**Independent Test**: `POST /v1/orders` with `usesDeferredPayment=true` returns `status=CREATED`, `paymentReference=null`, `paymentSource=null`.

- [x] T011 [US2] Add contract test to `src/contractTest/kotlin/com/elegant/software/blitzpay/order/OrderContractTest.kt` — deferred-payment order creation: `usesDeferredPayment=true`, asserts `status=CREATED`, `paymentReference` absent, `paymentSource=null`
- [x] T012 [US2] Verify `OrderService.createShopperOrder()` in `src/main/kotlin/com/elegant/software/blitzpay/order/application/OrderService.kt` — confirm deferred-payment orders are created without initiating payment (read-only check; no code change expected based on research)
- [x] T013 [US2] [P] Add contract test: existing Pay now create-order test still passes and `paymentSource=null` for a newly CREATED pay-now order (paymentSource only set on PAID transition)

---

## Phase 5: User Story 3 — Manage Unpaid Orders Before Payment Starts (P2)

**Goal**: Shopper can add/update/remove items and cancel; merchant can override line price; all guarded to CREATED status; all return full `OrderResponse`.

**Independent Test**: Create an unpaid order, add item, change quantity, apply merchant price override, remove item (not last), remove last item (auto-cancel). Verify each returns latest `OrderResponse` and final state is CANCELLED.

- [x] T014 [US3] Add `OrderMutationConflictException` class and `@ExceptionHandler` returning HTTP 409 in `src/main/kotlin/com/elegant/software/blitzpay/order/web/ShopperOrderController.kt` and `MerchantOrderController.kt`
- [x] T015 [US3] Add `addItem(orderId, request, actorId)` to `src/main/kotlin/com/elegant/software/blitzpay/order/application/OrderService.kt` — guard status=CREATED; resolve product via `merchantGateway`; append `OrderItem`; recalculate `order.totalAmountMinor` and `order.itemCount`; return `OrderResponse`
- [x] T016 [US3] [P] Add `updateItemQuantity(orderId, itemId, quantity, actorId)` to `OrderService.kt` — guard status=CREATED; validate quantity ≥ 1; update item quantity and `lineTotalMinor`; recalculate order totals; return `OrderResponse`
- [x] T017 [US3] [P] Add `removeItem(orderId, itemId, actorId)` to `OrderService.kt` — guard status=CREATED; if removing last item set `order.status=CANCELLED` and keep items for audit; else delete item; recalculate totals; return `OrderResponse`
- [x] T018 [US3] [P] Add `cancelOrder(orderId, actorId)` to `OrderService.kt` — guard status=CREATED; set `order.status=CANCELLED`; return `OrderResponse`
- [x] T019 [US3] [P] Add `applyMerchantPriceOverride(orderId, itemId, priceMinor, merchantUserId)` to `OrderService.kt` — guard status=CREATED; call `item.applyPriceOverride()`; recalculate order total; return `OrderResponse`
- [x] T020 [US3] Add `POST /{orderId}/items` endpoint to `src/main/kotlin/com/elegant/software/blitzpay/order/web/ShopperOrderController.kt` — calls `orderService.addItem()`; returns 200 OK with `OrderResponse`
- [x] T021 [US3] [P] Add `PATCH /{orderId}/items/{itemId}` endpoint to `ShopperOrderController.kt` — calls `orderService.updateItemQuantity()`; returns 200 OK
- [x] T022 [US3] [P] Add `DELETE /{orderId}/items/{itemId}` endpoint to `ShopperOrderController.kt` — calls `orderService.removeItem()`; returns 200 OK
- [x] T023 [US3] [P] Add `POST /{orderId}/cancel` endpoint to `ShopperOrderController.kt` — calls `orderService.cancelOrder()`; returns 200 OK
- [x] T024 [US3] Add `PATCH /{orderId}/items/{itemId}/price` endpoint to `src/main/kotlin/com/elegant/software/blitzpay/order/web/MerchantOrderController.kt` — calls `orderService.applyMerchantPriceOverride()`; returns 200 OK
- [x] T025 [US3] Add contract tests in `src/contractTest/kotlin/com/elegant/software/blitzpay/order/OrderContractTest.kt`:
  - Add item to unpaid order → 200, updated totalAmountMinor
  - Update quantity → 200, updated lineTotalMinor
  - Remove last item → 200, status=CANCELLED
  - Cancel order → 200, status=CANCELLED
  - Merchant price override → 200, unitPriceMinor reflects override
  - Mutation on non-CREATED order → 409

---

## Phase 6: User Story 4 — Resolve Unpaid Orders Through App Payment Or Manual Settlement (P2)

**Goal**: Merchant can manually settle CREATED or FAILED orders; response shows `paymentSource=MANUAL_SETTLEMENT`; duplicate settlement rejected with 409.

**Independent Test**: `POST /v1/merchant/orders/{orderId}/settle` on a CREATED deferred order → 200, `status=PAID`, `paymentSource=MANUAL_SETTLEMENT`. Same on FAILED order → 200. Same on already-PAID order → 409.

- [x] T026 [US4] Add `manualSettle(orderId, request, merchantUserId)` to `src/main/kotlin/com/elegant/software/blitzpay/order/application/OrderService.kt` — find order; call `order.manualSettle(note, merchantUserId)`; save; return `OrderResponse`
- [x] T027 [US4] Add `POST /{orderId}/settle` endpoint to `src/main/kotlin/com/elegant/software/blitzpay/order/web/MerchantOrderController.kt` — extracts merchant user from Authorization header; calls `orderService.manualSettle()`; returns 200 OK
- [x] T028 [US4] Add contract tests in `OrderContractTest.kt`:
  - Settle CREATED deferred order → 200, `paymentSource=MANUAL_SETTLEMENT`, `status=PAID`
  - Settle FAILED pay-now order → 200, `paymentSource=MANUAL_SETTLEMENT`
  - Settle already-PAID order → 409 Conflict

---

## Phase 7: User Story 5 — Read Order State Rich Enough For Shopper And Merchant UX (P3)

**Goal**: `paymentSource` visible on paid orders; merchant can GET single order by orderId; CREATED vs FAILED distinction already in status field.

**Independent Test**: `GET /v1/orders/{orderId}` on manually settled order returns `paymentSource=MANUAL_SETTLEMENT`; on app-paid order returns `paymentSource=APP_PAYMENT`. `GET /v1/merchant/orders/{orderId}` returns full `OrderResponse`.

- [x] T029 [US5] Add `getMerchantOrder(orderId, merchantId)` to `OrderService.kt` — finds order by orderId, validates it belongs to merchant; returns `OrderResponse`
- [x] T030 [US5] Add `GET /{orderId}` endpoint to `src/main/kotlin/com/elegant/software/blitzpay/order/web/MerchantOrderController.kt` — calls `orderService.getMerchantOrder()`; returns 200 OK
- [x] T031 [US5] [P] Add contract tests in `OrderContractTest.kt`:
  - GET order detail — manually settled: `paymentSource=MANUAL_SETTLEMENT`
  - GET order detail — app-paid: `paymentSource=APP_PAYMENT`
  - GET shopper orders list includes `paymentSource` and `usesDeferredPayment`
  - GET merchant branch orders list distinguishes CREATED vs FAILED status

---

## Phase 8: Polish & Validation

**Purpose**: Run full test suite, fix any regressions.

- [x] T032 Run `./gradlew check` and fix any compilation errors or failing tests
- [x] T033 Run `./gradlew contractTest` specifically and verify all new contract tests pass
- [x] T034 [P] Verify module boundaries still pass: run `ApplicationModules.of(...).verify()` test (check `src/test/kotlin` for existing module verification test)

---

## Dependencies

```
Phase 1 (T001–T003)
  └── Phase 2 (T004–T007)
        ├── Phase 3 / US1 (T008–T010)
        ├── Phase 4 / US2 (T011–T013)
        ├── Phase 5 / US3 (T014–T025)   ← depends on T015–T019 service methods
        ├── Phase 6 / US4 (T026–T028)   ← depends on T004 (manualSettle on Order)
        └── Phase 7 / US5 (T029–T031)   ← depends on T007 (paymentSource in response)
              └── Phase 8 (T032–T034)
```

US3 (Phase 5) and US4 (Phase 6) can be developed in parallel after Phase 2 completes.  
US5 (Phase 7) can begin after T007 (paymentSource in response model) is done.

## Implementation Strategy

**MVP (start here)**: Phases 1–4 = schema + foundational models + US1 + US2. Delivers deferred-payment order creation and merchant context visibility.

**Increment 2**: Phase 5 (US3) — unpaid order mutations.

**Increment 3**: Phase 6 (US4) — manual settlement.

**Full delivery**: Phase 7 (US5) + Phase 8 polish.

## Parallel Execution Examples

Within Phase 5, after T014–T015 complete, T016/T017/T018/T019 can run in parallel (separate service methods, same file — coordinate).

Within Phase 2, T005/T006 can run in parallel with T004 (different files).
