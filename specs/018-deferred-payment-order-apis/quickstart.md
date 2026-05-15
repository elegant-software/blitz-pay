# Quickstart: Deferred Payment Order APIs (018)

## Prerequisites

```bash
git checkout 018-deferred-payment-order-apis
./gradlew clean build       # verify baseline is green
```

## Implementation order

Work in this order to keep `./gradlew check` green after each step.

### Step 1 — Schema migrations

Create the two Liquibase changeset files:

**`src/main/resources/db/changelog/20260515-001-order-payment-source.sql`**
```sql
-- liquibase formatted sql
-- changeset mehdi:20260515-001-order-payment-source
ALTER TABLE blitzpay.order_orders
    ADD COLUMN payment_source VARCHAR(32) NULL,
    ADD COLUMN settlement_note VARCHAR(2000) NULL;
-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN settlement_note;
-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN payment_source;
```

**`src/main/resources/db/changelog/20260515-002-order-item-price-override.sql`**
```sql
-- liquibase formatted sql
-- changeset mehdi:20260515-002-order-item-price-override
ALTER TABLE blitzpay.order_items
    ADD COLUMN merchant_price_override_minor BIGINT NULL,
    ADD COLUMN overridden_by VARCHAR(255) NULL,
    ADD COLUMN overridden_at TIMESTAMPTZ NULL;
-- rollback ALTER TABLE blitzpay.order_items DROP COLUMN overridden_at;
-- rollback ALTER TABLE blitzpay.order_items DROP COLUMN overridden_by;
-- rollback ALTER TABLE blitzpay.order_items DROP COLUMN merchant_price_override_minor;
```

Register both in `db.changelog-master.yaml`.

### Step 2 — Entity changes

1. `Order.kt`: add `paymentSource`, `settlementNote` fields + `manualSettle()` method + extend `applyPaymentUpdate()` to set `paymentSource = "APP_PAYMENT"` on PAID transition.
2. `OrderItem.kt`: add `merchantPriceOverrideMinor`, `overriddenBy`, `overriddenAt` + `effectiveUnitPriceMinor` + `applyPriceOverride()`.

### Step 3 — API models

In `order/api/OrderModels.kt`:
- Add `PaymentSource` enum
- Add `SettleOrderRequest`, `AddOrderItemRequest`, `UpdateOrderItemRequest`, `MerchantItemPriceOverrideRequest`
- Add `paymentSource: PaymentSource?` to `OrderResponse` and `OrderSummaryResponse`
- Update `toResponse()` and `toSummaryResponse()` mappers

### Step 4 — MerchantContext extension

In `merchant/api/ProximityModels.kt`: add `deferredPaymentAvailable: Boolean = false` to `MerchantContext`.

In `merchant/application/ProximityService.kt` (or wherever `MerchantContext` is built): load offerings from `MerchantOfferingAssignmentRepository` and set `deferredPaymentAvailable = "DEFERRED_PAYMENT" in enabledOfferingCodes`.

### Step 5 — Service methods

In `OrderService.kt` add:
- `addItem(orderId, request, actorId)` — guard status CREATED, find product, add `OrderItem`, recalculate total
- `updateItemQuantity(orderId, itemId, quantity, actorId)` — guard, update, recalculate
- `removeItem(orderId, itemId, actorId)` — guard, check if last item (if so cancel), else delete item, recalculate
- `cancelOrder(orderId, actorId)` — guard status CREATED, set CANCELLED
- `applyMerchantPriceOverride(orderId, itemId, priceMinor, merchantUserId)` — guard status CREATED, update item
- `manualSettle(orderId, request, merchantUserId)` — guard CREATED or FAILED, call `order.manualSettle()`

All methods return `OrderResponse`. Transaction-scoped.

### Step 6 — Controllers

**`ShopperOrderController`**: add
- `POST /{orderId}/items` → `orderService.addItem()`
- `PATCH /{orderId}/items/{itemId}` → `orderService.updateItemQuantity()`
- `DELETE /{orderId}/items/{itemId}` → `orderService.removeItem()`
- `POST /{orderId}/cancel` → `orderService.cancelOrder()`

**`MerchantOrderController`**: add
- `PATCH /{orderId}/items/{itemId}/price` → `orderService.applyMerchantPriceOverride()`
- `POST /{orderId}/settle` → `orderService.manualSettle()`
- `GET /{orderId}` → `orderService.getMerchantOrder()` (new read by orderId, merchant-scoped)

Add exception handler for new `OrderMutationConflictException` returning 409.

### Step 7 — Contract tests

Add to `OrderContractTest.kt`:
- Deferred-payment order creation (`usesDeferredPayment=true`, `paymentSource=null`)
- Add item to unpaid order → 200 with updated totals
- Remove last item → 200 with `status=CANCELLED`
- Cancel order → 200 with `status=CANCELLED`
- Mutation rejected when not CREATED → 409
- Merchant price override → 200 with override price
- Manual settle → 200 with `paymentSource=MANUAL_SETTLEMENT`
- Settle already-paid order → 409
- Get order by orderId shows `paymentSource=APP_PAYMENT` when PAID via app

Add to proximity contract test:
- `deferredPaymentAvailable=true` when merchant has offering
- `deferredPaymentAvailable=false` when merchant does not

### Step 8 — Verify

```bash
./gradlew check
```

All unit + contract tests must be green before committing.

## Key file locations

| File | Purpose |
|------|---------|
| `src/main/kotlin/com/elegant/software/blitzpay/order/persistence/model/Order.kt` | Add payment_source, settlement_note |
| `src/main/kotlin/com/elegant/software/blitzpay/order/persistence/model/OrderItem.kt` | Add price override fields |
| `src/main/kotlin/com/elegant/software/blitzpay/order/api/OrderModels.kt` | New request/response models |
| `src/main/kotlin/com/elegant/software/blitzpay/order/application/OrderService.kt` | New service methods |
| `src/main/kotlin/com/elegant/software/blitzpay/order/web/ShopperOrderController.kt` | New shopper mutation endpoints |
| `src/main/kotlin/com/elegant/software/blitzpay/order/web/MerchantOrderController.kt` | New merchant endpoints |
| `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/ProximityModels.kt` | Add deferredPaymentAvailable |
| `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/ProximityService.kt` | Populate deferredPaymentAvailable |
| `src/contractTest/kotlin/com/elegant/software/blitzpay/order/OrderContractTest.kt` | Contract tests |
| `src/main/resources/db/changelog/20260515-001-order-payment-source.sql` | Migration: payment_source, settlement_note |
| `src/main/resources/db/changelog/20260515-002-order-item-price-override.sql` | Migration: price override columns |
