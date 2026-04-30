# Tasks: Order Payment Tracking

**Input**: Design documents from `/specs/009-order-payment-tracking/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Required — the constitution mandates coverage for every behavior change.

**Organization**: Tasks grouped by user story. Completed tasks are marked `[x]`. Tasks marked `[UPDATE]` in their description exist in code but must be revised to match the 2026-05-02 clarifications (new status enum, two creation paths, creator identity).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`…`[US5]`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Module and test scaffolding.

- [x] T001 Create order module package declarations in `src/main/kotlin/com/elegant/software/blitzpay/order/package-info.kt` and `src/main/kotlin/com/elegant/software/blitzpay/order/api/package-info.kt`
- [x] T002 [P] Create order test fixture directories and baseline JSON files in `src/test/resources/testdata/order/create-order/canonical-request.json` and `src/test/resources/testdata/order/payment-status/settled-event.json`
- [x] T003 [P] Create order test package scaffolding in `src/test/kotlin/com/elegant/software/blitzpay/order/` and `src/contractTest/kotlin/com/elegant/software/blitzpay/order/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, domain types, and shared module contracts that every user story depends on.

**⚠️ CRITICAL**: Complete all updates in this phase before user story work resumes.

- [x] T004 [UPDATE] Add `creator_type VARCHAR(16) NOT NULL` and `created_by_id VARCHAR(255) NOT NULL` columns plus `ix_order_orders_created_by_id` and `ix_order_orders_merchant_branch_id` indexes to the existing Liquibase changeset in `src/main/resources/db/changelog/20260430-001-create-order-tables.sql`
- [x] T005 [P] [UPDATE] Replace `OrderStatus` enum values (`PENDING_PAYMENT`, `PAYMENT_IN_PROGRESS`, `PAYMENT_FAILED` → `CREATED`, `PAYMENT_INITIATED`, `FAILED`) and update `isTerminal()` and all transition guards in `src/main/kotlin/com/elegant/software/blitzpay/order/domain/OrderStatus.kt` and `src/main/kotlin/com/elegant/software/blitzpay/order/domain/Order.kt`
- [x] T006 [P] Add `CreatorType` enum (`MERCHANT`, `SHOPPER`) in `src/main/kotlin/com/elegant/software/blitzpay/order/domain/CreatorType.kt` and add `createdById: String` and `creatorType: CreatorType` fields to `src/main/kotlin/com/elegant/software/blitzpay/order/domain/Order.kt`
- [x] T007 [P] Merchant API extension for orderable product snapshots in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantGateway.kt`
- [x] T008 [P] Order module repositories in `src/main/kotlin/com/elegant/software/blitzpay/order/repository/OrderRepository.kt`, `OrderItemRepository.kt`, and `PaymentAttemptRepository.kt`
- [x] T009 [P] Add `findByMerchantBranchIdAndCreatedAtBetween()` and `findByCreatedByIdAndCreatorTypeAndCreatedAtBetween()` query methods to `src/main/kotlin/com/elegant/software/blitzpay/order/repository/OrderRepository.kt`
- [x] T010 Modulith verification for the order module in `src/test/kotlin/com/elegant/software/blitzpay/order/OrderModularityTest.kt` and `src/test/kotlin/com/elegant/software/blitzpay/ModularityTest.kt`

**Checkpoint**: Updated schema, domain types with new enum and creator identity, and repository queries are ready.

---

## Phase 3: User Story 1 — Receive Orders From Merchant Products (Priority: P1) 🎯 MVP

**Goal**: Accept orders from both shoppers (immediate payment) and merchants (QR code returned). Persist order with item snapshots, creator identity, and totals.

**Independent Test**: `POST /v1/orders` with valid products + paymentMethod creates order, transitions to `PAYMENT_INITIATED`, returns payment reference. `POST /v1/merchant/orders` creates order in `CREATED` status, returns QR code. Both reject all-or-nothing on any invalid product with a per-item error list.

### Tests for User Story 1

- [x] T011 [P] [US1] [UPDATE] Extend contract test for `POST /v1/orders` to cover `paymentMethod` field, `PAYMENT_INITIATED` status, and `paymentRetryAllowed` in response in `src/contractTest/kotlin/com/elegant/software/blitzpay/order/OrderContractTest.kt`
- [x] T012 [P] [US1] Add contract test for `POST /v1/merchant/orders` covering `CREATED` status and QR code response in `src/contractTest/kotlin/com/elegant/software/blitzpay/order/OrderContractTest.kt`
- [x] T013 [P] [US1] [UPDATE] Update `OrderServiceTest` for new shopper creation path (payment dispatch), merchant creation path (QR code), creator identity fields, and all-or-nothing validation in `src/test/kotlin/com/elegant/software/blitzpay/order/application/OrderServiceTest.kt`
- [ ] T014 [P] [US1] Add test fixtures for merchant-create-order request and QR response in `src/test/resources/testdata/order/create-order/merchant-request.json` and `src/test/resources/testdata/order/create-order/merchant-response.json`

### Implementation for User Story 1

- [x] T015 [P] [US1] [UPDATE] Add `paymentMethod: PaymentMethod` to `CreateOrderRequest`; add `creatorType`, `createdById`, `paymentRetryAllowed` to `OrderResponse`; add `MerchantCreateOrderRequest`, `MerchantOrderResponse` (includes `qrCode`), and `OrderSummaryResponse` (for list endpoints) in `src/main/kotlin/com/elegant/software/blitzpay/order/api/OrderModels.kt`
- [x] T016 [US1] [UPDATE] Split `OrderService.create()` into `createShopperOrder()` (persists order, dispatches payment via provider gateway, transitions to `PAYMENT_INITIATED`, sets `creatorType = SHOPPER`) and `createMerchantOrder()` (persists order in `CREATED`, generates QR code, sets `creatorType = MERCHANT`) in `src/main/kotlin/com/elegant/software/blitzpay/order/application/OrderService.kt`
- [x] T017 [US1] Wire shopper payment dispatch: after order persistence call `OrderGateway.linkPaymentAttempt()` and invoke the appropriate provider (TrueLayer / QR / Braintree / Stripe) based on `paymentMethod`; transition order to `PAYMENT_INITIATED` in `src/main/kotlin/com/elegant/software/blitzpay/order/application/OrderService.kt`
- [x] T018 [US1] Wire merchant QR code generation: after order persistence, generate a QR code embedding the `orderId` (reuse `payments.qrpay` QR generation) and return it in `MerchantOrderResponse` in `src/main/kotlin/com/elegant/software/blitzpay/order/application/OrderService.kt`
- [x] T019 [US1] [UPDATE] Replace single `OrderController` with `ShopperOrderController` (`POST /v1/orders`, `GET /v1/orders`, `GET /v1/orders/{orderId}`) in `src/main/kotlin/com/elegant/software/blitzpay/order/web/ShopperOrderController.kt` and `MerchantOrderController` (`POST /v1/merchant/orders`, `GET /v1/merchant/orders`) in `src/main/kotlin/com/elegant/software/blitzpay/order/web/MerchantOrderController.kt`; delete `src/main/kotlin/com/elegant/software/blitzpay/order/web/OrderController.kt`

**Checkpoint**: Both creation paths independently functional; MVP order intake verified.

---

## Phase 4: User Story 2 — Track Payment Delivery Through Order Status (Priority: P1)

**Goal**: Order status is the authoritative business view of payment delivery; transitions are guarded; `PAID` is terminal.

**Independent Test**: Create order, apply `SETTLED` → `PAID`, `FAILED` → `FAILED`, verify `PAID` order ignores subsequent updates.

### Tests for User Story 2

- [x] T020 [P] [US2] [UPDATE] Update contract test for `GET /v1/orders/{orderId}` to assert new enum values (`CREATED`, `PAYMENT_INITIATED`, `PAID`, `FAILED`, `CANCELLED`) and `paymentRetryAllowed` field in `src/contractTest/kotlin/com/elegant/software/blitzpay/order/OrderContractTest.kt`
- [x] T021 [P] [US2] [UPDATE] Update status-transition and stale-update tests for new enum values and `FAILED` guard (was `PAYMENT_FAILED`) in `src/test/kotlin/com/elegant/software/blitzpay/order/application/OrderStatusProjectionTest.kt` and update fixture `src/test/resources/testdata/order/payment-status/settled-event.json`

### Implementation for User Story 2

- [x] T022 [US2] [UPDATE] Update `OrderStatusProjection.on()` to map `PaymentStatusCode` to new enum values (`PENDING/EXECUTED` → stay `PAYMENT_INITIATED`, `SETTLED` → `PAID`, `FAILED/EXPIRED` → `FAILED`) in `src/main/kotlin/com/elegant/software/blitzpay/order/application/OrderStatusProjection.kt`
- [x] T023 [US2] [UPDATE] Update `Order.applyPaymentUpdate()` transition guard to use new enum values and treat `FAILED` (not `PAYMENT_FAILED`) as retryable in `src/main/kotlin/com/elegant/software/blitzpay/order/domain/Order.kt`

**Checkpoint**: Payment delivery tracking works end-to-end with correct enum values and terminal-state protection.

---

## Phase 5: User Story 3 — Reuse Order ID As The Payment Reference (Priority: P2)

**Goal**: Every payment provider uses the `orderId` as the primary reference; payment attempts link back to the order.

**Independent Test**: Create an order, initiate QR/TrueLayer, Braintree, and Stripe payments, verify each payment attempt record references the same `orderId`.

### Tests for User Story 3

- [ ] T024 [P] [US3] Add/extend payment-attempt linking tests in `src/test/kotlin/com/elegant/software/blitzpay/order/application/PaymentAttemptServiceTest.kt`
- [ ] T025 [P] [US3] Extend contract coverage for `orderId` on Braintree and Stripe payment endpoints in `src/contractTest/kotlin/com/elegant/software/blitzpay/payments/braintree/BraintreePaymentControllerContractTest.kt` and `src/contractTest/kotlin/com/elegant/software/blitzpay/payments/stripe/StripePaymentControllerContractTest.kt`

### Implementation for User Story 3

- [x] T026 [US3] Implement `OrderGateway.assertPayable()` and `OrderGateway.linkPaymentAttempt()` in `src/main/kotlin/com/elegant/software/blitzpay/order/api/OrderGateway.kt` and `src/main/kotlin/com/elegant/software/blitzpay/order/application/PaymentAttemptService.kt`
- [ ] T027 [US3] Require and propagate `orderId` through QR/TrueLayer payment initiation in `src/main/kotlin/com/elegant/software/blitzpay/payments/qrpay/PaymentRequestController.kt` and `src/main/kotlin/com/elegant/software/blitzpay/payments/qrpay/PaymentInitRequestListener.kt`
- [ ] T028 [P] [US3] Propagate `orderId` through Braintree checkout in `src/main/kotlin/com/elegant/software/blitzpay/payments/braintree/internal/BraintreePaymentController.kt` and `src/main/kotlin/com/elegant/software/blitzpay/payments/braintree/internal/BraintreePaymentService.kt`
- [ ] T029 [P] [US3] Propagate `orderId` through Stripe intent creation in `src/main/kotlin/com/elegant/software/blitzpay/payments/stripe/internal/StripePaymentController.kt` and `src/main/kotlin/com/elegant/software/blitzpay/payments/stripe/internal/StripePaymentService.kt`

**Checkpoint**: One canonical `orderId` links all payment attempts and provider callbacks.

---

## Phase 6: User Story 4 — Merchant Lists Today's Orders (Priority: P2)

**Goal**: Authenticated merchant branch can retrieve today's full order details with optional status filter.

**Independent Test**: `GET /v1/merchant/orders` returns only today's orders for the authenticated branch in branch-local timezone; `?status=CREATED` returns only `CREATED` orders; other branches' orders are excluded; each result includes `paymentRetryAllowed`.

### Tests for User Story 4

- [ ] T030 [P] [US4] Add contract test for `GET /v1/merchant/orders` (no filter, status filter, empty result) in `src/contractTest/kotlin/com/elegant/software/blitzpay/order/OrderContractTest.kt`
- [ ] T031 [P] [US4] Add service tests for branch-scoped today-window query and status filter in `src/test/kotlin/com/elegant/software/blitzpay/order/application/OrderServiceTest.kt` and add fixture `src/test/resources/testdata/order/list-merchant-orders/today-orders.json`

### Implementation for User Story 4

- [ ] T032 [US4] Implement `OrderService.listMerchantOrders(branchId, status?, timezone)`: build today's `[startOfDay, endOfDay]` range in the branch-local timezone, call `OrderRepository.findByMerchantBranchIdAndCreatedAtBetween()` with optional status predicate, map to full `OrderResponse` list with `paymentRetryAllowed` in `src/main/kotlin/com/elegant/software/blitzpay/order/application/OrderService.kt`
- [ ] T033 [US4] Expose `GET /v1/merchant/orders` with optional `?status` query param and branch-scoped authorization in `src/main/kotlin/com/elegant/software/blitzpay/order/web/MerchantOrderController.kt`

**Checkpoint**: Merchant can review all of today's branch orders with status filtering.

---

## Phase 7: User Story 5 — Shopper Lists Recent Orders (Priority: P2)

**Goal**: Authenticated shopper can list their own order summaries and retrieve full detail for a single order.

**Independent Test**: `GET /v1/orders` returns only the authenticated shopper's last-7-days order summaries; `GET /v1/orders/{orderId}` returns full detail for own order and 403 for another shopper's order.

### Tests for User Story 5

- [ ] T034 [P] [US5] Add contract tests for `GET /v1/orders` (summary list) and `GET /v1/orders/{orderId}` (full detail + 403 cross-shopper) in `src/contractTest/kotlin/com/elegant/software/blitzpay/order/OrderContractTest.kt`
- [ ] T035 [P] [US5] Add service tests for shopper-scoped 7-day window and summary vs detail distinction in `src/test/kotlin/com/elegant/software/blitzpay/order/application/OrderServiceTest.kt` and add fixture `src/test/resources/testdata/order/list-shopper-orders/recent-orders.json`

### Implementation for User Story 5

- [ ] T036 [US5] Implement `OrderService.listShopperOrders(shopperId)`: query last-7-days orders where `createdById = shopperId` and `creatorType = SHOPPER`, return `List<OrderSummaryResponse>` in `src/main/kotlin/com/elegant/software/blitzpay/order/application/OrderService.kt`
- [ ] T037 [US5] Expose `GET /v1/orders` (summary list, 7-day default) and ensure `GET /v1/orders/{orderId}` enforces shopper identity check (403 if caller is not order owner) in `src/main/kotlin/com/elegant/software/blitzpay/order/web/ShopperOrderController.kt`

**Checkpoint**: Shoppers can browse their recent order history and drill into a single order.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T038 [P] Update `OrderOpenApiConfig` tags and descriptions for the two new controllers in `src/main/kotlin/com/elegant/software/blitzpay/order/config/OrderOpenApiConfig.kt`
- [ ] T039 Run full modulith verification and `./gradlew check` to confirm module boundaries, all contract tests, and unit tests pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1** (Setup): Done — no blockers.
- **Phase 2** (Foundational): T004–T006 must complete before any US work; T007–T010 already done.
- **Phase 3** (US1): Depends on Phase 2 updates (T004–T006). Delivers MVP.
- **Phase 4** (US2): Depends on Phase 2 (T005 enum rename). Builds on US1 order aggregate.
- **Phase 5** (US3): Depends on Phase 2 + US1 (order creation) + US2 (status tracking). T026 already done.
- **Phase 6** (US4): Depends on Phase 2 (T006 creator fields, T009 query methods) + US1 (order creation).
- **Phase 7** (US5): Depends on Phase 2 (T006, T009) + US1 (order creation).
- **Phase 8** (Polish): Depends on all desired user stories.

### User Story Dependencies

- **US1 → US4, US5**: List endpoints depend on orders existing with creator identity fields.
- **US1 → US3**: Payment reference linkage depends on created orders.
- **US2** is independent after Phase 2 (can be developed in parallel with US1 once enum is updated).
- **US4 and US5 can be developed in parallel** after US1 is complete.

### Parallel Opportunities Within Phases

- Phase 2: T005, T006, T007, T008, T009 can all run in parallel after T004 (schema first).
- Phase 3 tests: T011, T012, T013, T014 all parallel.
- Phase 3 impl: T015 (models) parallel with T016 start; T017 and T018 parallel after T016.
- Phase 5: T028 and T029 parallel after T027.
- Phase 6 and 7 can be developed in parallel after US1 completes.

---

## Parallel Example: Phase 3 (US1)

```bash
# Tests — write and verify they fail first:
Task: T011 — Update POST /v1/orders contract test
Task: T012 — Add POST /v1/merchant/orders contract test
Task: T013 — Update OrderServiceTest for two creation paths
Task: T014 — Add merchant creation fixtures

# Models + domain in parallel:
Task: T015 — Update OrderModels (add paymentMethod, creatorType, MerchantCreateOrderRequest, QR response)

# Then sequentially:
Task: T016 — Split OrderService into createShopperOrder / createMerchantOrder
Task: T017 — Wire shopper payment dispatch
Task: T018 — Wire merchant QR code generation
Task: T019 — Replace OrderController with ShopperOrderController + MerchantOrderController
```

---

## Parallel Example: Phase 6 + 7 (US4 + US5)

```bash
# After US1 is done, run US4 and US5 in parallel:
Dev A — Phase 6:
  Task: T030 — Merchant list contract test
  Task: T031 — Merchant list service test
  Task: T032 — OrderService.listMerchantOrders()
  Task: T033 — GET /v1/merchant/orders endpoint

Dev B — Phase 7:
  Task: T034 — Shopper list contract tests
  Task: T035 — Shopper list service test
  Task: T036 — OrderService.listShopperOrders()
  Task: T037 — GET /v1/orders endpoint + GET /v1/orders/{orderId} ownership check
```

---

## Implementation Strategy

### MVP (US1 + US2 only)

1. Complete Phase 2 updates (T004–T006, T009).
2. Complete Phase 3 (US1) — both creation paths working.
3. Complete Phase 4 (US2) — status tracking with new enum.
4. Validate independently: both `POST` endpoints, `GET /v1/orders/{orderId}`, full status lifecycle.

### Incremental Delivery

1. Setup + Foundational updates → schema and domain ready.
2. US1 → two creation paths, payment dispatch, QR code.
3. US2 → status tracking, event projection updated.
4. US3 → canonical payment reference across providers.
5. US4 + US5 in parallel → merchant and shopper list endpoints.
6. Polish → OpenAPI, full check.

---

## Notes

- `[x]` tasks are complete and do not need changes.
- `[UPDATE]` in description means the file exists but must be changed to match 2026-05-02 clarifications.
- `[P]` tasks touch different files with no unmet dependencies in the same phase.
- The suggested MVP scope is **US1 + US2** (order creation + status tracking with correct enum).
- Total tasks: 39 (17 already done or inherited `[x]`, 22 new or requiring updates).
