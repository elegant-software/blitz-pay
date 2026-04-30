# Quickstart: Order Payment Tracking

**Branch**: `009-order-payment-tracking`

## What is being added

A dedicated `order` business module with two creation paths and three read endpoints:

1. **Shopper creates order + pays**: `POST /v1/orders` — creates order, immediately initiates payment via the chosen provider, returns `orderId` + `PAYMENT_INITIATED` status + provider payment reference.
2. **Merchant creates order + gets QR**: `POST /v1/merchant/orders` — creates order, generates a QR code for the customer to scan, returns `orderId` + `CREATED` status + QR code. Payment fires when the customer scans.
3. **Shopper reads order list**: `GET /v1/orders` — returns summary records for the authenticated shopper's last 7 days of orders.
4. **Shopper reads order detail**: `GET /v1/orders/{orderId}` — returns full order detail including line items, payment state, and `paymentRetryAllowed`.
5. **Merchant reads branch orders**: `GET /v1/merchant/orders` — returns full order details for the authenticated branch's current-day orders; supports optional single-status filter.

## Order status lifecycle

```
CREATED → PAYMENT_INITIATED → PAID          (terminal)
                             → FAILED       → PAYMENT_INITIATED (retry)
                             → CANCELLED    → PAYMENT_INITIATED (retry)
CREATED → CANCELLED  (merchant cancels before QR scan)
```

`paymentRetryAllowed` is `true` for `CREATED`, `FAILED`, and `CANCELLED`; `false` for `PAYMENT_INITIATED` and `PAID`.

## Module design

- `merchant` continues to own merchant products and product pricing.
- `payments` continues to own payment execution and payment status normalization.
- `order` becomes the business source of truth for order records, order items, creator identity, and payment-attempt linkage.
- Cross-module calls: `order` → `merchant.api` (product validation + snapshot); `order` → `payments` (payment initiation on shopper orders); `order` listens to `PaymentStatusChanged` events to project payment outcomes onto order status.

## Creator identity

Every order stores `createdById` (user identity string) and `creatorType` (`MERCHANT` | `SHOPPER`). These drive read endpoint scoping and `paymentRetryAllowed` logic.

## Expected source layout

```text
src/main/kotlin/com/elegant/software/blitzpay/order/
├── api/
│   ├── OrderGateway.kt
│   ├── OrderModels.kt
│   └── package-info.kt
├── application/
│   ├── OrderService.kt
│   ├── OrderStatusProjection.kt
│   └── PaymentAttemptService.kt
├── domain/
│   ├── Order.kt
│   ├── OrderItem.kt
│   ├── OrderStatus.kt          (enum: CREATED, PAYMENT_INITIATED, PAID, FAILED, CANCELLED)
│   ├── CreatorType.kt          (enum: MERCHANT, SHOPPER)
│   └── PaymentAttempt.kt
├── repository/
│   ├── OrderRepository.kt
│   ├── OrderItemRepository.kt
│   └── PaymentAttemptRepository.kt
└── web/
    ├── ShopperOrderController.kt    (POST /v1/orders, GET /v1/orders, GET /v1/orders/{orderId})
    └── MerchantOrderController.kt   (POST /v1/merchant/orders, GET /v1/merchant/orders)
```

## Integration points

1. Add a `merchant.api` surface to resolve orderable product snapshots during order creation.
2. For shopper orders: after order persistence, dispatch payment to the appropriate provider via `payments` module, register a `PaymentAttemptLink`, and transition order to `PAYMENT_INITIATED`.
3. For merchant orders: after order persistence, generate a QR code embedding the `orderId`, return in response. No payment provider call at this stage.
4. For QR scan (merchant-created order): the customer scans, `payments.qrpay` calls back into `order.api` to register the payment attempt and transition order to `PAYMENT_INITIATED`.
5. Listen to `payments.push.api.PaymentStatusChanged` to project payment outcomes (`SETTLED` → `PAID`, `FAILED/EXPIRED` → `FAILED`) onto the owning order.

## Liquibase

Planned new changelog files:

- Create `order_orders` (includes `creator_type`, `created_by_id`)
- Create `order_items`
- Create `order_payment_attempts`
- Indexes: `ux_order_orders_order_id`, `ix_order_orders_merchant_branch_id`, `ix_order_orders_status`, `ix_order_orders_created_by_id`, `ux_order_payment_attempts_payment_request_id`, `ix_order_payment_attempts_order_id`

All table and index names follow the `order_` prefix rule from `CONSTITUTION.md`.

## Testing expectations

- Unit tests: order creation validation (all-or-nothing rejection), totals calculation, status transition rules, `paymentRetryAllowed` logic per status+creator combination
- Contract tests: all 5 endpoints, both creation paths (shopper + merchant), 422 response with per-product rejection reasons
- Modulith verification: `order` module depends only on `merchant.api` and `payments` published APIs/events
