# Quickstart: Order Payment Tracking

**Branch**: `009-order-payment-tracking`

## What is being added

This feature adds a dedicated order lifecycle to the system:

1. `POST /v1/orders` creates an order from merchant products.
2. `GET /v1/orders/{orderId}` returns the current order state and payment linkage.
3. Payment flows reuse the created `orderId` as the canonical business reference.
4. Payment status updates advance the order status automatically.

## Module design

- `merchant` continues to own merchant products and product pricing.
- `payments` continues to own payment execution and payment status normalization.
- `order` becomes the business source of truth for order records, order items, and payment-attempt linkage.

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
│   ├── OrderStatus.kt
│   └── PaymentAttempt.kt
├── repository/
│   ├── OrderRepository.kt
│   ├── OrderItemRepository.kt
│   └── PaymentAttemptRepository.kt
└── web/
    └── OrderController.kt
```

## Integration points

- Add a merchant API to resolve orderable product snapshots during order creation.
- Update payment initiation paths so they require `orderId` and register a payment attempt.
- Listen to `payments.push.api.PaymentStatusChanged` to project payment outcomes onto the owning order.

## Liquibase

Planned new changelog files:

- create `order_orders`
- create `order_items`
- create `order_payment_attempts`

All table and index names follow the module prefix rule from `CONSTITUTION.md`.

## Testing expectations

- Unit tests for order creation validation, totals, and status transitions
- Contract tests for create/read order endpoints and payment-reference changes
- Modulith verification to ensure the new `order` module only depends on published APIs and events
