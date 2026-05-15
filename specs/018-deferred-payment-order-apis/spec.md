# Feature Specification: Deferred Payment Order APIs

**Feature Branch**: `[018-deferred-payment-order-apis]`  
**Created**: 2026-05-15  
**Status**: Draft  
**Input**: User description: "Verify the backend APIs against the delayed payment order flow, make sure the required APIs are provided, and add a new Spec Kit spec for the missing backend requirements."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Show Deferred Payment Choice From Merchant Capability (Priority: P1)

A shopper must see whether a merchant supports deferred payment before confirming checkout so the app can present `Pay now` and `Pay later` only when the merchant allows it.

**Why this priority**: Without a backend-exposed merchant capability, the shopper app cannot correctly decide whether the deferred-payment journey is available at all.

**Independent Test**: Read merchant context for a merchant that supports deferred payment and one that does not, then verify the client can determine whether deferred payment is available before order creation.

**Acceptance Scenarios**:

1. **Given** a merchant has deferred payment enabled as a merchant-wide offering, **When** the shopper app reads merchant context, **Then** the response reveals that deferred payment is available for all of that merchant's branches.
2. **Given** a merchant does not have deferred payment enabled, **When** the shopper app reads merchant context, **Then** the response does not indicate deferred-payment availability.
3. **Given** a shopper is viewing a specific branch, **When** deferred payment is enabled merchant-wide, **Then** the branch inherits that capability without needing a separate branch-level override.

---

### User Story 2 - Create Orders For Pay Now And Pay Later (Priority: P1)

A shopper can create an order either for immediate payment or for deferred payment so the backend supports both checkout intents from the same order-first flow.

**Why this priority**: This is the central contract change. The current create-order contract assumes payment always starts immediately, which blocks deferred payment.

**Independent Test**: Submit one order as `Pay now` and one as `Pay later`, then verify both create successfully while only the immediate-payment order starts payment at creation time.

**Acceptance Scenarios**:

1. **Given** a shopper chooses `Pay now`, **When** the order is created, **Then** the order records immediate-payment intent and payment starts right away.
2. **Given** a shopper chooses `Pay later`, **When** the order is created, **Then** the order is created successfully without starting payment.
3. **Given** a shopper chooses `Pay later`, **When** the order is created, **Then** no payment method is required at creation time.
4. **Given** a shopper has not confirmed checkout yet, **When** they switch between `Pay now` and `Pay later` or leave the flow, **Then** no order is created.

---

### User Story 3 - Manage Unpaid Orders Before Payment Starts (Priority: P2)

A shopper and merchant can continue shaping an unpaid order before payment starts so the commercial content stays current without forcing a new order every time something changes.

**Why this priority**: Deferred payment only works operationally if unpaid orders can be corrected or expanded before payment begins.

**Independent Test**: Create an unpaid order, then add items, change quantities, remove items, cancel the order, and apply a merchant price override before payment starts, verifying each mutation returns the latest order state.

**Acceptance Scenarios**:

1. **Given** an unpaid order with no payment in progress, **When** the shopper or merchant changes line items before payment starts, **Then** the system updates the existing order instead of requiring a new one.
2. **Given** an unpaid order with no payment in progress, **When** the merchant changes the line price for a specific order line, **Then** the system stores that override for that line only.
3. **Given** an unpaid order, **When** either actor removes the final remaining item or explicitly cancels the order, **Then** the order becomes cancelled and is no longer payable.
4. **Given** payment has already started for the order, **When** either actor attempts an unpaid-order edit, **Then** the system rejects the edit.

---

### User Story 4 - Resolve Unpaid Orders Through App Payment Or Manual Settlement (Priority: P2)

A merchant can resolve unpaid orders either by waiting for the shopper to pay in the app or by manually settling qualifying orders that were paid outside the app so order state stays operationally accurate.

**Why this priority**: Deferred payment creates unpaid orders that may be resolved outside the app. The backend must represent that path explicitly.

**Independent Test**: Keep one deferred-payment order unpaid until the shopper pays in the app, and mark another qualifying unpaid order as manually settled, then verify both become paid with different payment-source values.

**Acceptance Scenarios**:

1. **Given** an original `Pay later` order is still unpaid, **When** the merchant records manual settlement, **Then** the order becomes paid with manual-settlement source.
2. **Given** a `Pay now` order failed and remains unpaid, **When** the merchant records manual settlement, **Then** the order becomes paid with manual-settlement source.
3. **Given** an order was paid successfully in the app or manually settled already, **When** a merchant tries to settle it again, **Then** the system rejects the duplicate settlement.
4. **Given** a merchant records manual settlement, **When** the order is read again by the shopper or merchant, **Then** the active state is paid and payment actions are no longer allowed.

---

### User Story 5 - Read Order State Rich Enough For Shopper And Merchant UX (Priority: P3)

A shopper app and merchant app can read orders with enough status detail to support retry, history, payment source, and merchant-facing unpaid-state distinctions without inventing logic client-side.

**Why this priority**: The apps need stable contract fields to present the deferred-payment journey coherently; otherwise the UI will guess from incomplete data.

**Independent Test**: Read recent shopper orders, a single order detail, and merchant branch orders across unpaid, failed, paid, cancelled, and manually settled examples, then verify the response fields support the expected UI states directly.

**Acceptance Scenarios**:

1. **Given** an order was created as `Pay later`, **When** the apps read it later, **Then** the original checkout intent remains visible as order detail.
2. **Given** an order is paid, **When** the apps read it, **Then** the response identifies whether payment came from app payment or manual settlement.
3. **Given** a merchant reads today's orders, **When** unpaid and failed orders are present, **Then** the response distinguishes “awaiting payment” from “payment failed”.
4. **Given** a `Pay now` order failed and is still recoverable, **When** the shopper reads it in recent orders or detail, **Then** the response still supports retry without pretending the original intent changed to `Pay later`.

### Edge Cases

- What happens when the shopper requests deferred payment for a merchant that does not enable that offering?
- What happens when a merchant tries to edit, cancel, or manually settle an order while payment is already in progress?
- How does the system respond when the same product is added to an unpaid order multiple times and the merchant overrides price for only one line?
- What happens when manual settlement is attempted for an order that is already paid, already cancelled, or otherwise no longer eligible?
- How does the system represent a failed `Pay now` order that remains unpaid and retryable without changing its original checkout intent?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose whether deferred payment is enabled as a merchant-wide offering in shopper-facing merchant context reads used before checkout.
- **FR-002**: Deferred-payment capability exposed to shoppers MUST apply uniformly across all branches of the merchant and MUST NOT require branch-level overrides.
- **FR-003**: The system MUST support two checkout intents for shopper-created orders: immediate payment and deferred payment.
- **FR-004**: When a shopper creates an order with immediate-payment intent, the system MUST create the order and start payment as part of the same confirmed checkout action.
- **FR-005**: When a shopper creates an order with deferred-payment intent, the system MUST create the order without starting payment.
- **FR-006**: The create-order contract for deferred payment MUST NOT require a payment method at order-creation time.
- **FR-007**: The system MUST persist the original checkout intent on the order so later reads can tell whether the order started as `Pay now` or `Pay later`.
- **FR-008**: The system MUST allow unpaid-order mutation before payment starts, including adding items, changing quantities, removing items, and explicit cancellation.
- **FR-009**: The system MUST allow both shopper and merchant actors to perform unpaid-order mutations before payment starts.
- **FR-010**: The system MUST allow merchant-only line-level price override before payment starts and MUST NOT allow shoppers to change price directly.
- **FR-011**: Shopper-added items MUST enter the unpaid order at the current catalog price unless the merchant later overrides the price for that line.
- **FR-012**: The system MUST support multiple separate order lines for the same product on an unpaid order and MUST apply merchant price override to a specific line rather than to every line of that product.
- **FR-013**: The system MUST reject unpaid-order edits whenever payment is in progress, after successful app payment, after manual settlement, or after cancellation.
- **FR-014**: If the final remaining item is removed from an unpaid order, the system MUST cancel the order instead of leaving an empty payable order.
- **FR-015**: The system MUST support merchant manual settlement for unpaid orders that originated as `Pay later`.
- **FR-016**: The system MUST support merchant manual settlement for unpaid orders whose original `Pay now` payment attempt failed.
- **FR-017**: Manual settlement MUST transition the order to a paid state and record `MANUAL_SETTLEMENT` as the payment source.
- **FR-018**: Manual settlement MAY include an optional merchant note and MUST NOT require a note.
- **FR-019**: The system MUST expose payment source on paid order reads with exactly two values for this feature: `APP_PAYMENT` and `MANUAL_SETTLEMENT`.
- **FR-020**: The system MUST expose shopper-order reads and merchant-order reads with enough information to distinguish original checkout intent, current business status, payment source, and retry eligibility.
- **FR-021**: Merchant-facing order reads MUST distinguish unpaid orders that are still awaiting first payment from unpaid orders whose latest payment attempt failed.
- **FR-022**: If a `Pay now` order fails, the system MUST keep the order unpaid and recoverable without changing the original checkout intent stored on the order.
- **FR-023**: A failed `Pay now` order that later becomes manually settled MUST present paid state and `MANUAL_SETTLEMENT` as the active outcome on subsequent reads.
- **FR-024**: Every unpaid-order mutation, cancellation, and manual-settlement action MUST return the latest authoritative order state so clients can refresh immediately from backend truth.

### Key Entities *(include if feature involves data)*

- **Deferred Payment Capability**: A merchant-wide ordering offering that makes `Pay later` available to the shopper for all of the merchant's branches.
- **Checkout Intent**: The shopper's original payment-timing choice for an order, distinguishing immediate payment from deferred payment.
- **Unpaid Order Mutation**: A pre-payment change to an existing unpaid order, including add-item, quantity change, remove-item, cancel, and merchant price override actions.
- **Merchant Price Override**: A merchant-controlled price set on a specific unpaid order line before payment starts.
- **Manual Settlement**: A merchant-recorded paid outcome for a qualifying unpaid order that was settled outside the shopper app.
- **Payment Source**: The recorded source of a paid order outcome, limited in this feature to `APP_PAYMENT` or `MANUAL_SETTLEMENT`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of merchants that enable deferred payment expose that capability in shopper-facing merchant context reads before checkout.
- **SC-002**: 100% of deferred-payment checkout confirmations create an order without requiring payment method selection at creation time.
- **SC-003**: 100% of immediate-payment checkout confirmations either create the order and start payment or fail clearly without creating an ambiguous partial state.
- **SC-004**: 100% of qualifying unpaid-order mutations return the latest authoritative order state in the same interaction.
- **SC-005**: 100% of paid order reads identify whether the order was settled by app payment or manual settlement.
- **SC-006**: 100% of merchant-order reads let the merchant distinguish unpaid “awaiting payment” orders from unpaid “payment failed” orders without client-side inference.

## Assumptions

- The backend remains the authority for merchant offerings, order lifecycle, and payment-state truth.
- Deferred payment is a merchant-wide offering already defined in the backend domain and does not require a new branch-level capability model.
- Shopper payment initiation continues to happen only through the shopper app, not through the merchant app.
- Existing payment-provider integrations remain in place for immediate-payment flows; this feature changes when payment is required, not which providers exist.
- Merchant-created orders in the existing merchant order endpoint are out of scope for this spec except where they share read models or order-state vocabulary.
- Shopper and merchant clients will refresh from the latest order snapshot returned by backend mutations rather than reconstructing state locally.
