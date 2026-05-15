# Data Model: Deferred Payment Order APIs (018)

## New / Modified DB Columns

### `order_orders` (migration: `20260515-001-order-payment-source.sql`)

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `payment_source` | `VARCHAR(32)` | YES | NULL | `APP_PAYMENT` or `MANUAL_SETTLEMENT`; NULL until order reaches PAID |
| `settlement_note` | `VARCHAR(2000)` | YES | NULL | Optional merchant note on manual settlement |

### `order_items` (migration: `20260515-002-order-item-price-override.sql`)

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `merchant_price_override_minor` | `BIGINT` | YES | NULL | Merchant-set unit price in minor units; overrides catalog price when present |
| `overridden_by` | `VARCHAR(255)` | YES | NULL | Merchant user ID who applied the override |
| `overridden_at` | `TIMESTAMPTZ` | YES | NULL | When the override was applied |

**Effective unit price**: `COALESCE(merchant_price_override_minor, unit_price_minor)`  
**Effective line total**: `effective_unit_price * quantity` — must be recalculated on every save.

---

## Entity Changes

### `Order` entity (`persistence/model/Order.kt`)

Add mutable fields:
```kotlin
@Column(name = "payment_source", length = 32)
var paymentSource: String? = null

@Column(name = "settlement_note", length = 2000)
var settlementNote: String? = null
```

Add domain method:
```kotlin
fun manualSettle(note: String?, by: String, at: Instant = Instant.now()) {
    require(status == OrderStatus.CREATED || status == OrderStatus.FAILED) {
        "Order cannot be manually settled in status: $status"
    }
    status = OrderStatus.PAID
    paymentSource = "MANUAL_SETTLEMENT"
    settlementNote = note
    paidAt = at
    updatedAt = at
}
```

Extend `applyPaymentUpdate()` to set `paymentSource = "APP_PAYMENT"` when `nextStatus == PAID`.

### `OrderItem` entity (`persistence/model/OrderItem.kt`)

Add fields:
```kotlin
@Column(name = "merchant_price_override_minor")
var merchantPriceOverrideMinor: Long? = null

@Column(name = "overridden_by", length = 255)
var overriddenBy: String? = null

@Column(name = "overridden_at")
var overriddenAt: Instant? = null
```

Computed effective unit price:
```kotlin
val effectiveUnitPriceMinor: Long
    get() = merchantPriceOverrideMinor ?: unitPriceMinor
```

Add method:
```kotlin
fun applyPriceOverride(priceMinor: Long, by: String, at: Instant = Instant.now()) {
    require(priceMinor > 0) { "Override price must be positive" }
    merchantPriceOverrideMinor = priceMinor
    lineTotalMinor = priceMinor * quantity
    overriddenBy = by
    overriddenAt = at
}
```

### `MerchantContext` (`merchant/api/ProximityModels.kt`)

Add field:
```kotlin
data class MerchantContext(
    val merchantId: UUID,
    val name: String,
    val logoUrl: String? = null,
    val activePaymentChannels: Set<MerchantPaymentChannel> = emptySet(),
    val deferredPaymentAvailable: Boolean = false,   // NEW
    val branches: List<BranchContext> = emptyList(),
)
```

---

## New API Models (`order/api/OrderModels.kt`)

```kotlin
enum class PaymentSource { APP_PAYMENT, MANUAL_SETTLEMENT }

// Settle unpaid order
data class SettleOrderRequest(
    val note: String? = null,
)

// Add item to unpaid order
data class AddOrderItemRequest(
    val productId: UUID,
    val quantity: Int,
)

// Change quantity on existing order item
data class UpdateOrderItemRequest(
    val quantity: Int,
)

// Merchant price override on a specific item
data class MerchantItemPriceOverrideRequest(
    val unitPriceMinor: Long,
)
```

Extend `OrderResponse` and `OrderSummaryResponse`:
```kotlin
data class OrderResponse(
    ...
    val paymentSource: PaymentSource? = null,   // NEW: set on PAID orders
    ...
)

data class OrderSummaryResponse(
    ...
    val paymentSource: PaymentSource? = null,   // NEW
    ...
)
```

---

## State Transitions

```
CREATED ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────── │
   │  (deferred: awaiting payment)                                                                                                                                        │
   │  (immediate: client initiates payment)                                                                                                                               │
   │                                                                                                                                                                      │
   ├──[startPayment]──→ PAYMENT_INITIATED ──[webhook PAID]──→ PAID (paymentSource=APP_PAYMENT)                                                                           │
   │                           │                                                                                                                                          │
   │                           └──[webhook FAILED]──→ FAILED ──[startPayment retry]──→ PAYMENT_INITIATED                                                                 │
   │                                                     │                                                                                                               │
   │                                                     └──[merchant settle]──→ PAID (paymentSource=MANUAL_SETTLEMENT)                                                  │
   │                                                                                                                                                                      │
   ├──[merchant settle (deferred)]──→ PAID (paymentSource=MANUAL_SETTLEMENT)                                                                                             │
   │                                                                                                                                                                      │
   ├──[cancel / removeLastItem]──→ CANCELLED                                                                                                                             │
   │                                                                                                                                                                      │
   └──[mutateItems (add/change/remove)]──→ CREATED (quantity/total updated)
```

**Mutation guard**: `mutateItems`, `priceOverride`, and `cancel` are only allowed from `CREATED` status. HTTP 409 returned otherwise.

**Manual settlement guard**: Only from `CREATED` or `FAILED`. HTTP 409 if `PAID` or `CANCELLED`.

---

## Validation Rules

| Rule | Details |
|------|---------|
| Deferred payment capability | `usesDeferredPayment=true` requires DEFERRED_PAYMENT in merchant offerings — already validated by `validateOfferingRules()` |
| Mutation guard | Status must be `CREATED`; reject with 409 if `PAYMENT_INITIATED`, `PAID`, `FAILED`, or `CANCELLED` |
| Empty order auto-cancel | Removing last item automatically transitions to CANCELLED (no empty payable orders) |
| Quantity minimum | `quantity >= 1` on add and update |
| Override price positive | `merchantPriceOverrideMinor > 0` |
| Settlement guard | Status must be `CREATED` or `FAILED`; reject 409 if `PAID` or `CANCELLED` |
| Duplicate settlement | Attempting to settle an already PAID order returns 409 |
| Shopper price edit | Shoppers cannot call the price-override endpoint (403 or separate endpoint by path) |
