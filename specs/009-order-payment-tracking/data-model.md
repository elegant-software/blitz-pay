# Data Model: Order Payment Tracking

**Branch**: `009-order-payment-tracking` | **Phase**: 1 | **Date**: 2026-04-24

## New Module-Owned Entities

### Order

**Table**: `blitzpay.order_orders`

| Field | Type | Nullable | Notes |
|------|------|----------|------|
| `id` | `UUID` | NO | Internal primary key |
| `order_id` | `VARCHAR(64)` | NO | External business reference used across payment flows |
| `merchant_application_id` | `UUID` | NO | Owning merchant |
| `merchant_branch_id` | `UUID` | YES | Optional branch scope when all items are branch-bound |
| `status` | `VARCHAR(32)` | NO | `CREATED`, `PAYMENT_INITIATED`, `PAID`, `FAILED`, `CANCELLED` |
| `creator_type` | `VARCHAR(16)` | NO | `MERCHANT` or `SHOPPER` — actor who created the order |
| `created_by_id` | `VARCHAR(255)` | NO | Identity of the creator (shopper user ID or merchant branch user ID) |
| `currency` | `VARCHAR(3)` | NO | ISO currency code |
| `total_amount_minor` | `BIGINT` | NO | Final order amount in minor units |
| `item_count` | `INTEGER` | NO | Sum of submitted quantities |
| `created_at` | `TIMESTAMPTZ` | NO | Creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | NO | Last status or data update |
| `paid_at` | `TIMESTAMPTZ` | YES | Set when order reaches `PAID` |
| `last_payment_request_id` | `UUID` | YES | Most recent linked payment attempt |
| `last_payment_provider` | `VARCHAR(32)` | YES | `TRUELAYER`, `BRAINTREE`, `STRIPE`, etc. |

**Validation rules**:
- `order_id` must be unique.
- `status` must be one of `CREATED`, `PAYMENT_INITIATED`, `PAID`, `FAILED`, `CANCELLED`; transitions must follow the defined state machine.
- `creator_type` must be `MERCHANT` or `SHOPPER`.
- `total_amount_minor` must equal the sum of order item line totals.
- `currency` must be consistent across all items in a single order.

### OrderItem

**Table**: `blitzpay.order_items`

| Field | Type | Nullable | Notes |
|------|------|----------|------|
| `id` | `UUID` | NO | Primary key |
| `order_id_fk` | `UUID` | NO | FK to `order_orders.id` |
| `merchant_product_id` | `UUID` | NO | Source product ID |
| `merchant_application_id` | `UUID` | NO | Snapshot ownership |
| `merchant_branch_id` | `UUID` | YES | Snapshot branch scope |
| `product_name` | `VARCHAR(255)` | NO | Snapshot name |
| `product_description` | `VARCHAR(2000)` | YES | Snapshot description |
| `quantity` | `INTEGER` | NO | Must be positive |
| `unit_price_minor` | `BIGINT` | NO | Snapshot unit price in minor units |
| `line_total_minor` | `BIGINT` | NO | `quantity * unit_price_minor` |
| `created_at` | `TIMESTAMPTZ` | NO | Creation timestamp |

**Validation rules**:
- `quantity` must be greater than zero.
- `line_total_minor` must equal `quantity * unit_price_minor`.

### PaymentAttemptLink

**Table**: `blitzpay.order_payment_attempts`

| Field | Type | Nullable | Notes |
|------|------|----------|------|
| `id` | `UUID` | NO | Primary key |
| `order_id_fk` | `UUID` | NO | FK to `order_orders.id` |
| `order_id` | `VARCHAR(64)` | NO | Denormalized business reference for lookup |
| `payment_request_id` | `UUID` | NO | Payment-module identifier |
| `provider` | `VARCHAR(32)` | NO | `TRUELAYER`, `BRAINTREE`, `STRIPE`, etc. |
| `provider_reference` | `VARCHAR(255)` | YES | Provider-side payment ID or transaction ID |
| `status` | `VARCHAR(32)` | NO | Normalized payment attempt status |
| `created_at` | `TIMESTAMPTZ` | NO | Link creation time |
| `updated_at` | `TIMESTAMPTZ` | NO | Latest payment update |

**Validation rules**:
- `payment_request_id` must be unique.
- One order may have many payment attempts, but only one successful (`PAID`) terminal outcome.

## Relationships

- One `Order` has many `OrderItem` rows.
- One `Order` has zero or many `PaymentAttemptLink` rows.
- `Order` references merchant ownership by ID only; merchant details stay in the `merchant` module.
- `OrderItem` snapshots product data at creation time and must not be rewritten from later merchant-product edits.

## State Transitions

### Order Status

```text
CREATED
  -> PAYMENT_INITIATED  (shopper: on payment dispatch; merchant: on customer QR scan)
  -> CANCELLED          (merchant cancels before QR is scanned)

PAYMENT_INITIATED
  -> PAID
  -> FAILED
  -> CANCELLED

FAILED
  -> PAYMENT_INITIATED  (retry: new payment attempt registered)

CANCELLED
  -> PAYMENT_INITIATED  (retry if business allows re-opening)

PAID
  -> terminal
```

`paymentRetryAllowed` rules:
- `CREATED` → `true` (shopper: payment dispatch failed; merchant: no scan yet)
- `PAYMENT_INITIATED` → `false` (in-flight)
- `PAID` → `false` (terminal)
- `FAILED` → `true`
- `CANCELLED` → `true`

### Payment Event Mapping

| Payment Event | Order Transition |
|--------------|------------------|
| Payment dispatched by `order` module | `CREATED` → `PAYMENT_INITIATED` |
| `PaymentStatusCode.PENDING` | stay `PAYMENT_INITIATED` |
| `PaymentStatusCode.EXECUTED` | stay `PAYMENT_INITIATED` |
| `PaymentStatusCode.SETTLED` | `PAYMENT_INITIATED` → `PAID` |
| `PaymentStatusCode.FAILED` | `PAYMENT_INITIATED` → `FAILED` |
| `PaymentStatusCode.EXPIRED` | `PAYMENT_INITIATED` → `FAILED` |
| QR code scanned by customer (merchant-created order) | `CREATED` → `PAYMENT_INITIATED` |

## Cross-Module API Additions

### Merchant Product Lookup Contract

The `merchant` module exposes a new API surface for order creation to retrieve orderable product snapshots:

- `findOrderableProducts(productIds: Collection<UUID>): List<OrderableMerchantProduct>`

Returned data includes:
- product ID
- merchant application ID
- optional branch ID
- name and description
- unit price
- active flag

### Order Payment Linking Contract

The `order` module exposes an API surface for payment modules to register payment attempts:

- `linkPaymentAttempt(orderId: String, paymentRequestId: UUID, provider: String, providerReference: String?)`
- `assertPayable(orderId: String): OrderPaymentSummary`

## Liquibase Changes

Planned changesets:

1. Create `blitzpay.order_orders` (includes `creator_type`, `created_by_id`)
2. Create `blitzpay.order_items`
3. Create `blitzpay.order_payment_attempts`
4. Add supporting indexes:
   - `ux_order_orders_order_id`
   - `ix_order_orders_merchant_application_id`
   - `ix_order_orders_merchant_branch_id`
   - `ix_order_orders_status`
   - `ix_order_orders_created_by_id`
   - `ux_order_payment_attempts_payment_request_id`
   - `ix_order_payment_attempts_order_id`
