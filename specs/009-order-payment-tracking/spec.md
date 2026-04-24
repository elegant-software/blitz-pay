# Feature Specification: Order Payment Tracking

**Feature Branch**: `[009-order-payment-tracking]`  
**Created**: 2026-04-24  
**Status**: Draft  
**Input**: User description: "I want to have OrderService implemenation here based on merchants product, Order will be recieved by system and status column is needed to track payment deliver and order Id is needed as a refrence for all payments"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receive Orders From Merchant Products (Priority: P1)

A merchant-facing client submits an order to the system using products that belong to a merchant, and the system creates a single order record that captures the ordered items, totals, and merchant ownership.

**Why this priority**: Without reliable order intake, there is no stable business object to pay against or track through the rest of the payment flow.

**Independent Test**: Submit an order containing valid merchant products and verify the system creates one order with a unique order identifier, linked merchant context, and the expected line items and totals.

**Acceptance Scenarios**:

1. **Given** active merchant products are available for ordering, **When** a client submits an order with valid product selections and quantities, **Then** the system creates one order record with a unique order ID, the selected line items, and the calculated order amount.
2. **Given** an order request references products that do not belong to the intended merchant or are not currently orderable, **When** the client submits the order, **Then** the system rejects the order and explains which product selections are invalid.

---

### User Story 2 - Track Payment Delivery Through Order Status (Priority: P1)

An operator or connected payment process can inspect an order and understand whether payment is still pending, has been delivered successfully, failed, or was cancelled, using the order status as the authoritative business view.

**Why this priority**: The request explicitly needs a status field to track payment delivery, and downstream payment behavior cannot be understood or supported without a durable order state.

**Independent Test**: Create an order, move it through payment-related events, and verify the order status changes correctly and remains queryable as the latest business state.

**Acceptance Scenarios**:

1. **Given** a newly received order, **When** no successful payment delivery has been recorded yet, **Then** the order remains in an unpaid or pending payment state.
2. **Given** an existing order awaiting payment completion, **When** the system receives confirmation that payment was delivered successfully, **Then** the order status changes to a paid state and the change is recorded against that order.
3. **Given** an existing order awaiting payment completion, **When** payment delivery fails or is explicitly cancelled, **Then** the order status changes to the appropriate failure or cancelled state without losing the original order reference.

---

### User Story 3 - Reuse Order ID As The Payment Reference (Priority: P2)

A payment flow uses the system-generated order ID as the common reference across all payment attempts and providers so that reconciliation, support, and status tracking all point to the same order.

**Why this priority**: Reusing the same order ID across payment flows reduces fragmentation and makes reconciliation consistent, but it depends on the core order lifecycle already existing.

**Independent Test**: Create an order and initiate payments through different supported payment flows, then verify each payment record and callback refers back to the same order ID.

**Acceptance Scenarios**:

1. **Given** an order has been created, **When** a payment is initiated for that order, **Then** the payment flow uses the order ID as the primary external reference.
2. **Given** multiple payment attempts exist for the same order, **When** payment events or support queries are processed, **Then** all related activity can be traced back to the single order ID.

### Edge Cases

- What happens when an order request contains a mix of valid and invalid products?
- How does the system handle a payment confirmation that arrives for an unknown or already finalised order?
- What happens when the same order is submitted for payment more than once after it has already been marked as paid?
- How does the system behave when merchant product details change after an order has already been created?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept order submissions that reference merchant-owned products and create a single persisted order for each accepted submission.
- **FR-002**: The system MUST assign a unique order ID to every accepted order at creation time.
- **FR-003**: The system MUST validate that every ordered item belongs to the intended merchant context and is currently eligible to be ordered before accepting the order.
- **FR-004**: The system MUST store the ordered items, quantities, captured product details, and resulting order total as part of the order record.
- **FR-005**: The system MUST persist an order status for every order and make that status queryable as the current business state of payment delivery.
- **FR-006**: The system MUST initialize newly created orders in a status that clearly indicates payment has not yet been delivered.
- **FR-007**: The system MUST allow order status to move from its initial state into successful, failed, or cancelled payment outcomes based on payment lifecycle events.
- **FR-008**: The system MUST preserve the full order record and order ID even when payment fails or is cancelled.
- **FR-009**: The system MUST use the order ID as the reference value for every payment initiated from that order, regardless of payment method.
- **FR-010**: The system MUST ensure inbound payment updates can be matched back to the originating order by using the order ID reference.
- **FR-011**: The system MUST prevent a successfully paid order from being marked as newly unpaid by duplicate or stale payment updates.
- **FR-012**: The system MUST provide clear failure feedback when an order cannot be created because referenced merchant products are missing, inactive, or inconsistent.

### Key Entities *(include if feature involves data)*

- **Order**: A merchant-owned commercial transaction created from selected products, carrying the order ID, current status, totals, and payment reference lifecycle.
- **Order Item**: A captured line within an order representing a selected merchant product, quantity, unit pricing snapshot, and line total.
- **Payment Reference**: The business linkage between an order and any payment attempt, callback, or reconciliation event, using the order ID as the common identifier.
- **Merchant Product Snapshot**: The product information captured at order creation time so the order remains understandable even if the live product catalog changes later.

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
- Existing payment flows will continue to handle payment execution, while this feature introduces the order record as the common reference point.
- The system may capture a snapshot of product information at order time so later catalog edits do not rewrite historical orders.
