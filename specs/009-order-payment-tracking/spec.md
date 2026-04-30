# Feature Specification: Order Payment Tracking

**Feature Branch**: `[009-order-payment-tracking]`  
**Created**: 2026-04-24  
**Status**: Draft  
**Input**: User description: "I want to have OrderService implemenation here based on merchants product, Order will be recieved by system and status column is needed to track payment deliver and order Id is needed as a refrence for all payments"

## Clarifications

### Session 2026-05-02

- Q: Does order creation trigger payment automatically, or is payment initiated by a separate subsequent call? → A: Depends on who creates the order — see Q3.
- Q: What are the canonical order status enum values? → A: `CREATED`, `PAYMENT_INITIATED`, `PAID`, `FAILED`, `CANCELLED` — finer-grained set that tracks payment initiation as a distinct state.
- Q: Who creates orders and how is payment triggered? → A: Both shoppers and merchants create orders via separate endpoints. When a shopper creates an order, payment is initiated immediately via the chosen payment provider. When a merchant creates an order (e.g., POS), payment is deferred — the system generates a QR code that the customer scans via their app to initiate payment.
- Q: Should product details be snapshotted at order creation, and should creator identity be captured? → A: Always capture a full product snapshot (name, price, unit) at order creation time; always record the identity of who created the order and whether they were a MERCHANT or SHOPPER.
- Q: If an order contains a mix of valid and invalid products, should the system partially accept or reject entirely? → A: Reject the entire order; the error response must list every invalid product and the reason each was rejected.

### Session 2026-05-01

- Q: For `GET /v1/merchant/orders`, what timezone defines the default `today` window? → A: Merchant/branch local timezone
- Q: Should `GET /v1/merchant/orders` return summaries only or full order details? → A: Full order details including line items
- Q: Should the optional `status` filter accept one status or multiple statuses per request? → A: One status per request
- Q: How should the response indicate whether an unpaid order can continue payment? → A: Include `paymentRetryAllowed` boolean on each order
- Q: Should `GET /v1/merchant/orders` return all merchant branches or only the authenticated branch? → A: Only the authenticated branch
- Q: For shopper recent orders, who may access `GET /v1/orders`? → A: Only the authenticated shopper identity
- Q: What is the default `GET /v1/orders` recent-orders window for shoppers? → A: Last 7 days
- Q: Should shopper recent-orders return summaries or full details? → A: `GET /v1/orders` returns summaries; `GET /v1/orders/{orderId}` returns full details

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receive Orders From Merchant Products (Priority: P1)

Both shoppers and merchants can submit orders using products from a merchant's catalog. The system creates a single order record capturing the ordered items, totals, and merchant ownership. Payment is triggered differently by each actor: shopper-created orders initiate payment immediately; merchant-created orders produce a QR code for the customer to scan and pay.

**Why this priority**: Without reliable order intake, there is no stable business object to pay against or track through the rest of the payment flow.

**Independent Test**: Submit an order as a shopper and verify payment is initiated immediately. Submit an order as a merchant and verify a QR code is returned and payment is not initiated until the customer scans it.

**Acceptance Scenarios**:

1. **Given** a shopper submits an order with valid product selections, quantities, and a chosen payment method, **When** the order is accepted, **Then** the system creates one order record with a unique order ID and immediately initiates payment via the provider determined by the chosen payment method.
2. **Given** a merchant creates an order via the merchant endpoint (POS flow), **When** the order is accepted, **Then** the system creates one order record with a unique order ID and returns a QR code; payment is deferred until the customer scans the QR code via their app.
3. **Given** an order request references products that do not belong to the intended merchant or are not currently orderable, **When** the client submits the order, **Then** the system rejects the order and explains which product selections are invalid.

---

### User Story 2 - Track Payment Delivery Through Order Status (Priority: P1)

An operator or connected payment process can inspect an order and understand whether payment is still pending, has been delivered successfully, failed, or was cancelled, using the order status as the authoritative business view.

**Why this priority**: The request explicitly needs a status field to track payment delivery, and downstream payment behavior cannot be understood or supported without a durable order state.

**Independent Test**: Create an order, move it through payment-related events, and verify the order status changes correctly and remains queryable as the latest business state.

**Acceptance Scenarios**:

1. **Given** a newly received order, **When** no successful payment delivery has been recorded yet, **Then** the order status is `CREATED` or `PAYMENT_INITIATED` and is never `PAID`.
2. **Given** an order in `PAYMENT_INITIATED` status, **When** the system receives confirmation that payment was delivered successfully, **Then** the order status transitions to `PAID` and the change is recorded against that order.
3. **Given** an order in `PAYMENT_INITIATED` status, **When** payment delivery fails or is explicitly cancelled, **Then** the order status transitions to `FAILED` or `CANCELLED` respectively, without losing the original order reference.

---

### User Story 3 - Reuse Order ID As The Payment Reference (Priority: P2)

A payment flow uses the system-generated order ID as the common reference across all payment attempts and providers so that reconciliation, support, and status tracking all point to the same order.

**Why this priority**: Reusing the same order ID across payment flows reduces fragmentation and makes reconciliation consistent, but it depends on the core order lifecycle already existing.

**Independent Test**: Create an order and initiate payments through different supported payment flows, then verify each payment record and callback refers back to the same order ID.

**Acceptance Scenarios**:

1. **Given** an order has been created, **When** a payment is initiated for that order, **Then** the payment flow uses the order ID as the primary external reference.
2. **Given** multiple payment attempts exist for the same order, **When** payment events or support queries are processed, **Then** all related activity can be traced back to the single order ID.

---

### User Story 4 - Merchant Lists Today's Orders (Priority: P2)

A merchant-facing client can retrieve the current day's orders for its authenticated branch scope so staff can review order progress without editing order data.

**Why this priority**: Merchant operators need a fast operational view of recent orders after order creation and payment tracking already exist, but the flow is read-only for this iteration.

**Independent Test**: Request `GET /v1/merchant/orders` as an authenticated merchant-branch user and verify the response includes only that branch's orders created during the merchant or branch local calendar day, with optional filtering by order status.

**Acceptance Scenarios**:

1. **Given** an authenticated branch user with orders created today, **When** the client requests `GET /v1/merchant/orders` without filters, **Then** the system returns only that branch's orders from the current merchant or branch local calendar day.
2. **Given** an authenticated branch user with orders in multiple statuses today, **When** the client supplies a single status filter value, **Then** the system returns only today's orders matching that status within the same authenticated branch scope.
3. **Given** orders belonging to other branches or merchants, **When** a branch user requests `GET /v1/merchant/orders`, **Then** the system excludes orders outside the authenticated branch scope.
4. **Given** no orders exist for the authenticated branch today, **When** the client requests `GET /v1/merchant/orders`, **Then** the system returns an empty read-only result set rather than an error.
5. **Given** an authenticated branch user requests `GET /v1/merchant/orders`, **When** orders are returned, **Then** each order entry includes full order details, including line items, totals, order status, identifiers, and a `paymentRetryAllowed` flag needed to continue payment for unpaid orders.

---

### User Story 5 - Shopper Lists Recent Orders (Priority: P2)

An authenticated shopper can retrieve their own recent orders through `GET /v1/orders` and open a single order through `GET /v1/orders/{orderId}` to review status and continue payment where allowed.

**Why this priority**: Shopper order history is a direct user-facing follow-up to order creation and payment tracking, and it requires a distinct actor model from merchant operational order views.

**Independent Test**: Request `GET /v1/orders` as an authenticated shopper and verify the response includes only summary records for that shopper's recent orders; request `GET /v1/orders/{orderId}` for one returned order and verify the full detail matches the shopper's accessible order.

**Acceptance Scenarios**:

1. **Given** an authenticated shopper with recent orders, **When** the shopper requests `GET /v1/orders`, **Then** the system returns only orders belonging to that authenticated shopper identity from the last 7 days by default.
2. **Given** orders belonging to different shoppers, **When** one shopper requests `GET /v1/orders`, **Then** the system excludes orders belonging to any other shopper.
3. **Given** an authenticated shopper requests `GET /v1/orders`, **When** recent orders are returned, **Then** each result is an order summary rather than the full line-item detail.
4. **Given** an authenticated shopper requests `GET /v1/orders/{orderId}` for one of their own orders, **When** the order exists, **Then** the system returns the single full order detail.
5. **Given** an authenticated shopper requests `GET /v1/orders/{orderId}` for another shopper's order, **When** the order exists, **Then** the system denies access.

### Edge Cases

- **Mixed valid/invalid products**: If any ordered item is invalid (wrong merchant, inactive, or not orderable), the entire order is rejected. The error response lists every invalid product reference and the specific reason for each rejection.
- **Payment confirmation for unknown or already-finalised order**: A payment update for an unknown order ID is discarded with a warning log. A payment update for a `PAID` order is discarded without changing status (FR-011 — idempotency guard).
- **Duplicate payment attempt on a paid order**: Ignored; the order remains `PAID` and the duplicate update does not create a new payment record or alter the order.
- **Merchant product details change after order creation**: No effect on existing orders — the product snapshot captured at creation time is the authoritative record for that order's line items.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept order submissions that reference merchant-owned products and create a single persisted order for each accepted submission.
- **FR-002**: The system MUST assign a unique order ID to every accepted order at creation time.
- **FR-003**: The system MUST validate that every ordered item belongs to the intended merchant context and is currently eligible to be ordered before accepting the order; if any item fails validation the entire order is rejected (all-or-nothing).
- **FR-004**: The system MUST store the ordered items, quantities, captured product snapshot (name, price, unit at time of order), and resulting order total as part of the order record.
- **FR-004a**: The system MUST record the creator's identity (user ID) and actor type (`MERCHANT` or `SHOPPER`) on every order at creation time.
- **FR-005**: The system MUST persist an order status for every order and make that status queryable as the current business state of payment delivery.
- **FR-006**: The system MUST initialize newly created orders with status `CREATED`, then immediately transition to `PAYMENT_INITIATED` once the payment provider call is dispatched.
- **FR-007**: The system MUST support the following order status transitions based on payment lifecycle events: `CREATED` → `PAYMENT_INITIATED` (on payment dispatch); `PAYMENT_INITIATED` → `PAID` (on confirmed payment delivery); `PAYMENT_INITIATED` → `FAILED` (on payment failure); `PAYMENT_INITIATED` → `CANCELLED` (on cancellation); `FAILED` or `CANCELLED` → `PAYMENT_INITIATED` (on payment retry).
- **FR-008**: The system MUST preserve the full order record and order ID even when payment fails or is cancelled.
- **FR-009**: The system MUST use the order ID as the reference value for every payment initiated from that order, regardless of payment method.
- **FR-010**: The system MUST ensure inbound payment updates can be matched back to the originating order by using the order ID reference.
- **FR-011**: The system MUST prevent an order in `PAID` status from being transitioned to any other status by duplicate or stale payment updates.
- **FR-012**: When an order is rejected due to invalid products, the system MUST return a failure response that identifies every invalid product reference and the specific reason each was rejected (missing, inactive, wrong merchant, etc.).
- **FR-013**: The system MUST provide a read-only `GET /v1/merchant/orders` endpoint that lists orders for the authenticated branch scope.
- **FR-014**: When `GET /v1/merchant/orders` is called without an explicit date filter, the system MUST return only orders created during the current merchant or branch local calendar day.
- **FR-015**: The system MUST support an optional single-value order-status filter on `GET /v1/merchant/orders`; accepted values are the canonical status enum: `CREATED`, `PAYMENT_INITIATED`, `PAID`, `FAILED`, `CANCELLED`.
- **FR-016**: The system MUST enforce branch-scoped authorization on `GET /v1/merchant/orders` so callers can access only orders for their authenticated branch.
- **FR-017**: `GET /v1/merchant/orders` MUST return full order details for each matching order, including line items, totals, current order status, and order identifiers.
- **FR-018**: Each order returned by `GET /v1/merchant/orders` MUST include a `paymentRetryAllowed` boolean that explicitly indicates whether the client may resume payment for that order.
- **FR-019**: The system MUST provide a read-only shopper endpoint `GET /v1/orders` that returns recent orders for the authenticated shopper identity only.
- **FR-020**: The system MUST provide `GET /v1/orders/{orderId}` as the shopper-facing single-order detail endpoint.
- **FR-021**: The system MUST enforce shopper-scoped authorization on `GET /v1/orders` and `GET /v1/orders/{orderId}` so shoppers can access only their own orders.
- **FR-022**: When `GET /v1/orders` is called without an explicit date filter, the system MUST return the authenticated shopper's orders from the last 7 days by default.
- **FR-023**: `GET /v1/orders` MUST return shopper-order summary records, while `GET /v1/orders/{orderId}` MUST return the full order detail.
- **FR-024**: When a shopper creates an order, the system MUST initiate payment immediately via the provider determined by the chosen payment method, passing the order ID as the payment reference.
- **FR-025**: When a merchant creates an order, the system MUST generate a QR code referencing the order ID and return it to the merchant; payment is not initiated until the customer scans the QR code via their app, at which point the QR pay flow initiates payment and transitions the order to `PAYMENT_INITIATED`.
- **FR-026**: The shopper order creation request MUST include a payment method that the system uses to select the appropriate payment provider.

### Key Entities *(include if feature involves data)*

- **Order**: A merchant-owned commercial transaction created from selected products, carrying the order ID, current status, totals, payment reference lifecycle, creator identity (`createdById`), and creator actor type (`creatorType`: `MERCHANT` | `SHOPPER`). Status enum: `CREATED` (order persisted, payment dispatch pending) → `PAYMENT_INITIATED` (payment call sent to provider) → `PAID` | `FAILED` | `CANCELLED`. `FAILED` and `CANCELLED` orders may transition back to `PAYMENT_INITIATED` on retry. `paymentRetryAllowed` is `true` for `CREATED`, `FAILED`, and `CANCELLED`; `false` for `PAYMENT_INITIATED` and `PAID`.
- **Merchant Order List Response**: A read-only collection of branch-scoped orders for the requested day, where each entry contains the full order detail needed for operational review and unpaid-order payment continuation.
- **Shopper Order List Response**: A read-only collection of shopper-scoped recent-order summaries returned by `GET /v1/orders`.
- **Order Item**: A captured line within an order representing a selected merchant product, quantity, unit pricing snapshot, and line total.
- **Payment Reference**: The business linkage between an order and any payment attempt, callback, or reconciliation event, using the order ID as the common identifier.
- **Merchant Product Snapshot**: The product information (name, price, unit) captured at order creation time and stored as part of the order item, so the order remains accurate and auditable even if the live product catalog changes later.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of accepted orders receive a unique order ID before any payment is initiated.
- **SC-002**: 100% of payment attempts created from an order can be traced back to exactly one order ID.
- **SC-003**: 95% of valid order submissions complete order creation in under 3 seconds under normal operating conditions.
- **SC-004**: 100% of payment delivery outcomes recorded by the system result in a visible, current order status that operators can inspect without reviewing raw payment events.
- **SC-005**: Support or reconciliation users can identify the correct order for a payment-related inquiry using the order ID alone in at least 95% of sampled cases.

## Assumptions

- Orders are created from products that already exist in the merchant product catalog maintained by the system.
- A single order belongs to one merchant context and does not mix products from different merchants.
- The first release focuses on capturing orders and tracking payment delivery status, not on shipment, fulfilment, or refund workflows.
- When a shopper creates an order, the system immediately initiates payment via the provider selected by the chosen payment method; the order ID is used as the payment reference. When a merchant creates an order, the system generates a QR code referencing the order ID; payment is initiated when the customer scans the QR code via their app. Multiple payment providers are supported; provider routing is determined by the payment method.
- Product information (name, price, unit) is always snapshotted at order creation time; later catalog edits do not affect historical order records.
- The order creator's identity and actor type (`MERCHANT` or `SHOPPER`) are always recorded at order creation time.
- For merchant order listing, the default `today` window is calculated using the merchant or branch local timezone rather than server-local time or UTC.
- `GET /v1/merchant/orders` is branch-scoped for the first release and does not aggregate orders across multiple branches.
- Shopper-facing order reads are scoped strictly to the authenticated shopper identity rather than merchant or branch operators.
- Shopper recent-order listing defaults to a rolling last-7-days window.
