# Research: Order Payment Tracking

**Branch**: `009-order-payment-tracking` | **Phase**: 0 | **Date**: 2026-04-24

## 1. Module Ownership

**Decision**: Create a new top-level `order` Spring Modulith module.

**Rationale**: Orders are a separate business capability from merchant catalog management and payment execution. The `merchant` module should continue owning products and branches, while the `payments` module continues owning provider-specific payment execution. The new `order` module becomes the single owner of order persistence, order status, and payment-attempt linkage.

**Alternatives considered**:
- Extend the `merchant` module: rejected because order status and payment attempt tracking are not catalog concerns and would make `merchant` own cross-cutting payment state.
- Extend the `payments` module: rejected because orders must exist before payment and should remain queryable even if no payment is ever initiated.
- Reuse the `invoice` module: rejected because the current invoice module is document-generation oriented and does not own transactional lifecycle state.

## 2. Merchant Product Validation Strategy

**Decision**: The `order` module will validate ordered products through a dedicated public merchant API surface, not by querying merchant repositories directly.

**Rationale**: The constitution forbids cross-module table access. Product ownership, activity, pricing, and merchant association remain merchant concerns. A dedicated lookup API keeps the `order` module decoupled from merchant internals while still allowing synchronous validation during order creation.

**Alternatives considered**:
- Direct repository access from `order` to `merchant`: rejected because it breaks module ownership rules.
- Fully asynchronous validation through events: rejected because order creation must fail fast when products are invalid.

## 3. Order Data Snapshot Strategy

**Decision**: Capture a product snapshot in each order item at the time of order creation, including product ID, product name, merchant ownership, unit price, and optional branch context.

**Rationale**: Orders must remain auditable even if the live product catalog changes later. Storing only product IDs would make historical orders dependent on mutable merchant data.

**Alternatives considered**:
- Resolve all display and pricing data dynamically from `merchant_products`: rejected because historical accuracy would be lost after product edits or deactivation.

## 4. Order Status Model

**Decision**: Use a five-value lifecycle enum: `CREATED`, `PAYMENT_INITIATED`, `PAID`, `FAILED`, `CANCELLED`.

**Rationale**: `CREATED` captures the window between order persistence and payment dispatch — a distinct and observable state, especially for merchant-created orders where payment is deferred until the customer scans the QR code. `PAYMENT_INITIATED` replaces the former `PAYMENT_IN_PROGRESS` to align with the fact that payment is initiated as part of, or immediately after, order creation. `FAILED` and `CANCELLED` are kept separate to support retry logic and audit trails. `PAID` is terminal.

**Alternatives considered**:
- `PENDING_PAYMENT` as initial state: rejected because it conflates "not yet dispatched" and "dispatched but not confirmed", making the merchant QR code flow ambiguous.
- Mirror payment provider status values directly on the order: rejected because it leaks provider semantics and complicates client behavior.
- Keep only `PENDING` and `PAID`: rejected because it loses important failure and in-progress states needed for support and retry handling.

## 5. Payment Event Mapping

**Decision**: Map payment module status events to order statuses using the existing `PaymentStatusChanged` event stream plus a new persisted payment-attempt link from `paymentRequestId` to `orderId`.

**Rationale**: The payments push module already emits normalized payment status events keyed by `paymentRequestId`. The order module can remain provider-agnostic by linking payment requests back to orders and translating those updates into order status transitions.

**Mapped transitions**:
- `PENDING` → order stays `PAYMENT_INITIATED`
- `EXECUTED` → order stays `PAYMENT_INITIATED` (provider executing but not settled)
- `SETTLED` → `PAID`
- `FAILED` → `FAILED`
- `EXPIRED` → `FAILED`

**Alternatives considered**:
- Poll payment status endpoints from the order module: rejected because event-driven updates are already available and avoid duplicated coordination logic.
- Store payment provider state only in the payments module and not on orders: rejected because the feature explicitly requires order status as the business-facing source of truth.

## 6. Payment Reference Propagation

**Decision**: Treat the system-generated order ID as the canonical business reference across all payment providers, including the existing QR/TrueLayer flow and Braintree checkout.

**Rationale**: The codebase already carries `orderId` through the TrueLayer `PaymentRequested` contract. Extending the same idea to all payment flows gives support and reconciliation one stable identifier to search by.

**Alternatives considered**:
- Continue using `productId` or `invoiceId` as payment references in some providers: rejected because these identifiers describe different concepts and cannot unify payment tracking at the order level.

## 7. Order Creation Interface

**Decision**: Two dedicated creation endpoints based on actor: `POST /v1/orders` (shopper) and `POST /v1/merchant/orders` (merchant). Read operations: `GET /v1/orders` (shopper list), `GET /v1/orders/{orderId}` (shopper detail), `GET /v1/merchant/orders` (merchant branch list).

**Rationale**: Shopper and merchant creation paths are fundamentally different — they differ in who authenticates, what payment trigger fires, and what the response contains. Keeping them on separate paths keeps authorization, validation, and response shape cleanly separated. Read endpoints likewise split by actor to enforce scoping rules (branch-scoped for merchant, identity-scoped for shopper).

**Alternatives considered**:
- Single `POST /v1/orders` endpoint for both actors with a role-based dispatcher: rejected because the response shapes differ (payment reference vs QR code) and shared authorization logic would be harder to reason about.
- Implicitly create orders inside payment initiation endpoints: rejected because it couples order persistence to a single payment path and prevents merchant-created QR orders from existing independently.

## 8. Payment Initiation as Part of Order Creation

**Decision**: Payment is automatically triggered at order creation time, not as a separate client call. The trigger mechanism depends on the creator type.

- **Shopper-created orders**: `POST /v1/orders` includes a `paymentMethod` field. The `order` module dispatches a payment initiation request to the appropriate provider immediately after persisting the order. The order transitions from `CREATED` to `PAYMENT_INITIATED` in the same request scope. The response includes the order details and a provider-specific payment reference (e.g., TrueLayer payment link, Braintree client token).
- **Merchant-created orders**: `POST /v1/merchant/orders` does not accept a `paymentMethod`. Instead, the `order` module generates a QR code linking to the order ID and returns it in the response. The order stays in `CREATED` until the customer scans the QR code via their app, at which point the existing `payments.qrpay` module initiates payment and the order transitions to `PAYMENT_INITIATED`.

**Rationale**: This design keeps payment initiation as a consequence of order creation without coupling order persistence to any single payment path. It enables retries (for failed payments), QR-based deferred payment, and multi-provider routing all from a consistent order lifecycle.

**Alternatives considered**:
- Require a separate `POST /v1/payments/initiate` call after order creation: rejected because clients would need to coordinate two calls, introducing a window of partially-created orders with no payment in flight.

## 9. Creator Identity Capture

**Decision**: Store `created_by_id` (user identity string) and `creator_type` (`MERCHANT` | `SHOPPER`) on every order row at creation time.

**Rationale**: Creator identity is required for correct scoping on read endpoints: `GET /v1/orders` must filter by `created_by_id` for shoppers; `GET /v1/merchant/orders` must filter by `merchant_branch_id`. The `creator_type` field makes it possible to distinguish merchant-created (QR flow) orders from shopper-created (direct payment) orders at a glance, and is needed for `paymentRetryAllowed` logic (QR orders in `CREATED` status are still awaiting first scan, not failed retries).

**Alternatives considered**:
- Infer creator type from presence of payment method in the order: rejected because it conflates creation time data with payment state, and makes future reporting harder.
