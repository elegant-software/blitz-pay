# Research: Deferred Payment Order APIs (018)

## What already exists

### Order module foundation

| Component | File | Status |
|-----------|------|--------|
| `order_orders.uses_deferred_payment` (BOOLEAN NOT NULL DEFAULT FALSE) | `20260514-001-merchant-refactor.sql` | ✅ Exists |
| `order_orders.order_type` (VARCHAR 32 NOT NULL DEFAULT 'PRE_ORDER') | `20260514-001-merchant-refactor.sql` | ✅ Exists |
| `Order.usesDeferredPayment` + `Order.orderType` entity fields | `persistence/model/Order.kt` | ✅ Exists |
| `CreateOrderRequest.usesDeferredPayment` | `api/OrderModels.kt` | ✅ Exists |
| `OrderResponse.usesDeferredPayment` + `orderType` | `api/OrderModels.kt` | ✅ Exists |
| `OrderService.validateOfferingRules()` — checks DEFERRED_PAYMENT offering | `service/OrderService.kt:219` | ✅ Exists |
| `OrderStatus`: CREATED, PAYMENT_INITIATED, PAID, FAILED, CANCELLED | `persistence/model/OrderStatus.kt` | ✅ Exists |
| `merchant_offerings` table with DEFERRED_PAYMENT seed row | `20260514-001-merchant-refactor.sql` | ✅ Exists |
| `merchant_application_offerings` junction table | `20260514-001-merchant-refactor.sql` | ✅ Exists |

### Deferred-payment checkout intent (FR-003 through FR-007)

`createShopperOrder()` already creates the order entity and returns it. No payment initiation happens at creation time — the `paymentMethod` field is captured in the request but the service does not publish a payment event or call a payment provider. Payment initiation is a separate client-driven step. This means:

- `usesDeferredPayment=true` → order created at CREATED status, no payment started. ✅ Already works.
- `usesDeferredPayment=false` → order created at CREATED status, client initiates payment separately.

FR-003/FR-004/FR-005/FR-006 are satisfied by the existing create-order flow. No changes needed to `createShopperOrder()` beyond the `checkoutIntent` persistence that is already in `usesDeferredPayment`.

### Merchant context (FR-001 / FR-002)

- `GET /v1/merchants/{id}` returns `MerchantDetailsResponse` which includes `offerings: Set<String>`.
  - `MerchantManagementService.get()` populates offerings from `MerchantOfferingAssignmentRepository` (line 148).
  - This endpoint is admin-facing. It DOES expose offerings already. **Gap**: the endpoint is identified by UUID, not merchantCode, and it's not clearly shopper-facing.
- `MerchantContext` in `ProximityResponse` (returned when shopper enters geofence) does NOT include offerings or `deferredPaymentAvailable`.
- **Decision**: Add `deferredPaymentAvailable: Boolean` to `MerchantContext` and populate it in `ProximityService` using `MerchantOfferingAssignmentRepository`. This is the lightest-touch change and covers the shopper-app checkout path (shopper enters geofence → receives merchant context with deferred-payment flag before creating order).

## Gaps to implement

### Gap 1 — Merchant context: deferredPaymentAvailable (US1)

**Decision**: Extend `MerchantContext` with `deferredPaymentAvailable: Boolean = false`.  
**Rationale**: `ProximityService` already reads the merchant from DB; one additional query to `MerchantOfferingAssignmentRepository` for the merchant's enabled offerings is sufficient. No new endpoint needed — the proximity event already returns `MerchantContext` to the shopper app. The `GET /v1/merchants/{id}` already returns offerings for admin/merchant-app reads; the shopper path is the proximity response.  
**Alternatives considered**: New dedicated `GET /v1/merchants/{merchantCode}/context` endpoint — rejected as unnecessary surface area since proximity already carries the right context.

### Gap 2 — Unpaid order mutations (US3)

**Decision**: Add four new endpoints in the `order` module for shopper + merchant mutations on CREATED-status orders:

| Endpoint | Actor | Purpose |
|----------|-------|---------|
| `POST /v1/orders/{orderId}/items` | Shopper | Add item at catalog price |
| `PATCH /v1/orders/{orderId}/items/{itemId}` | Shopper | Change quantity (min 1) |
| `DELETE /v1/orders/{orderId}/items/{itemId}` | Shopper | Remove item (auto-cancel if last) |
| `POST /v1/orders/{orderId}/cancel` | Shopper | Explicit cancellation |
| `PATCH /v1/merchant/orders/{orderId}/items/{itemId}/price` | Merchant | Override unit price on specific line |

All mutation endpoints return the full `OrderResponse` (FR-024).

**Guard rule**: Any mutation attempted when `status != CREATED` returns HTTP 409 Conflict (FR-013).

**Auto-cancel on last-item removal** (FR-014): `DELETE .../items/{itemId}` checks if removing the item would leave zero items; if so, sets `status = CANCELLED` and does not delete items but flags order cancelled (items are kept for audit).

**Merchant price override** (FR-010, FR-012): Stores on the specific `order_items` row. New column `merchant_price_override_minor BIGINT NULL` — when set, effective unit price = override; `line_total_minor` recalculated. `overridden_by VARCHAR(255)` and `overridden_at TIMESTAMPTZ` for audit.

### Gap 3 — Payment source and manual settlement (US4)

**Decision**:
- New `payment_source VARCHAR(32) NULL` column on `order_orders` — set to `APP_PAYMENT` when payment webhook transitions to PAID, set to `MANUAL_SETTLEMENT` when merchant manually settles.
- New `settlement_note VARCHAR(2000) NULL` column on `order_orders`.
- New endpoint `POST /v1/merchant/orders/{orderId}/settle` — transitions CREATED or FAILED order to PAID with `MANUAL_SETTLEMENT` source (FR-015, FR-016, FR-017, FR-018).
- Reject if `status == PAID` or `status == CANCELLED` (FR-019 / FR-023).
- Set `paidAt = now()`.

**Alternatives considered**: Separate `order_settlements` table — rejected as unnecessary complexity; `payment_source` is a single-value field on the order, not a list.

### Gap 4 — Enriched order reads (US5)

**Decision**: Add `paymentSource: String?` to `OrderResponse` and `OrderSummaryResponse`.  
**Rationale**: Clients need `paymentSource` to distinguish `APP_PAYMENT` from `MANUAL_SETTLEMENT` on paid orders (FR-019). The existing `status` field already distinguishes CREATED (awaiting payment) from FAILED (payment failed) for the merchant-facing distinction in FR-021. No additional status values needed.

## Schema changes summary

| Migration file | Change |
|---------------|--------|
| `20260515-001-order-payment-source.sql` | Add `payment_source VARCHAR(32) NULL` and `settlement_note VARCHAR(2000) NULL` to `order_orders` |
| `20260515-002-order-item-price-override.sql` | Add `merchant_price_override_minor BIGINT NULL`, `overridden_by VARCHAR(255) NULL`, `overridden_at TIMESTAMPTZ NULL` to `order_items` |

## Resolved clarifications

| # | Question | Answer |
|---|----------|--------|
| 1 | Is deferred-payment checkout intent already persisted? | Yes — `usesDeferredPayment` on `order_orders`. FR-007 satisfied. |
| 2 | Does Pay now initiate payment at order creation? | No — payment initiation is client-driven (separate call). Order creation always returns CREATED. |
| 3 | Are merchant-order reads already returning status? | Yes — `GET /v1/merchant/orders?branchId=` already returns `OrderResponse` with `status`. CREATED vs FAILED distinction is enough for FR-021. |
| 4 | Is a new GET merchant context endpoint needed? | No — extend `MerchantContext` in ProximityResponse with `deferredPaymentAvailable`. |
| 5 | Do order items need a composite key? | No — `id UUID PK` on `order_items` is sufficient; mutation targets item by `id`. |
