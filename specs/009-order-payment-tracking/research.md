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

**Decision**: Use an explicit order payment lifecycle: `PENDING_PAYMENT`, `PAYMENT_IN_PROGRESS`, `PAID`, `PAYMENT_FAILED`, `CANCELLED`.

**Rationale**: The user request is centered on tracking payment delivery, so the status model should stay business-facing and compact. `PENDING_PAYMENT` is the initial state after order creation. Provider updates then move the order forward without exposing raw provider-specific status codes to callers.

**Alternatives considered**:
- Mirror payment provider status values directly on the order: rejected because it leaks provider semantics and complicates client behavior.
- Keep only `PENDING` and `PAID`: rejected because it loses important failure and in-progress states needed for support and retry handling.

## 5. Payment Event Mapping

**Decision**: Map payment module status events to order statuses using the existing `PaymentStatusChanged` event stream plus a new persisted payment-attempt link from `paymentRequestId` to `orderId`.

**Rationale**: The payments push module already emits normalized payment status events keyed by `paymentRequestId`. The order module can remain provider-agnostic by linking payment requests back to orders and translating those updates into order status transitions.

**Mapped transitions**:
- `PENDING` -> `PENDING_PAYMENT`
- `EXECUTED` -> `PAYMENT_IN_PROGRESS`
- `SETTLED` -> `PAID`
- `FAILED` -> `PAYMENT_FAILED`
- `EXPIRED` -> `PAYMENT_FAILED`

**Alternatives considered**:
- Poll payment status endpoints from the order module: rejected because event-driven updates are already available and avoid duplicated coordination logic.
- Store payment provider state only in the payments module and not on orders: rejected because the feature explicitly requires order status as the business-facing source of truth.

## 6. Payment Reference Propagation

**Decision**: Treat the system-generated order ID as the canonical business reference across all payment providers, including the existing QR/TrueLayer flow and Braintree checkout.

**Rationale**: The codebase already carries `orderId` through the TrueLayer `PaymentRequested` contract. Extending the same idea to all payment flows gives support and reconciliation one stable identifier to search by.

**Alternatives considered**:
- Continue using `productId` or `invoiceId` as payment references in some providers: rejected because these identifiers describe different concepts and cannot unify payment tracking at the order level.

## 7. Order Creation Interface

**Decision**: Add dedicated order endpoints for create and read operations: `POST /v1/orders` and `GET /v1/orders/{orderId}`.

**Rationale**: Order creation and order status lookup are new external capabilities and should be modeled explicitly as first-class API contracts instead of being hidden inside payment endpoints.

**Alternatives considered**:
- Implicitly create orders inside payment initiation endpoints: rejected because it couples order persistence to a single payment path and prevents unpaid orders from existing as standalone business records.
