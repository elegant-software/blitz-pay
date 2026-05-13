# Feature Specification: Merchant Refactor

**Feature Branch**: `017-merchant-refactor`  
**Created**: 2026-05-14  
**Status**: Draft  
**Input**: User description: "implement merchant refcatoring based on CONTEXT.md file"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manage operational merchants and branches (Priority: P1)

An operator can create and maintain an operational merchant record without onboarding workflow data, assign a stable merchant code, maintain optional merchant-level address and contact information, and add branches with their own stable codes, mandatory address, mandatory location, and independent branch status.

**Why this priority**: The refactor exists to replace onboarding-flavoured merchant records with an operational merchant model. Without this, the rest of the domain remains inconsistent.

**Independent Test**: Create a merchant with no branches, then add a branch and verify the merchant and branch are stored, identified, and retrieved using the new operational rules.

**Acceptance Scenarios**:

1. **Given** an operator needs to create a new merchant, **When** they submit merchant details without onboarding fields, **Then** the system creates an operational merchant with a system-generated merchant code, editable merchant name, simple merchant status, and optional merchant address, location, and contact info.
2. **Given** an existing merchant has no branches yet, **When** an operator creates the first branch, **Then** the system assigns a system-generated branch code unique within that merchant and requires branch name, branch status, branch address, and branch location.
3. **Given** a branch already exists, **When** its display name or contact override changes, **Then** the system updates those editable branch-facing details without changing the branch code.

---

### User Story 2 - Configure merchant-wide offerings and enforce order rules (Priority: P1)

A merchant can enable customer-facing offerings from a fixed catalogue, and customer order flows are allowed only when the relevant offering is enabled and the branch and merchant statuses allow traffic.

**Why this priority**: Offerings are central to the refactor because they replace ambiguous branch-vs-merchant behaviour and directly govern which customer flows are valid.

**Independent Test**: Enable and disable merchant offerings, then attempt pre-order and walk-in order creation against a branch to verify allowed flows succeed and disallowed flows are rejected.

**Acceptance Scenarios**:

1. **Given** a merchant has enabled `PRE_ORDER`, **When** a customer requests a pre-order for a branch, **Then** the system allows the request regardless of customer proximity, subject to normal branch and merchant status checks.
2. **Given** a merchant has enabled `WALK_IN_ORDERING`, **When** a customer requests walk-in ordering with recent and sufficiently accurate geolocation inside the branch area, **Then** the system allows the request.
3. **Given** a merchant has not enabled the relevant offering, **When** a customer attempts the corresponding order flow, **Then** the system rejects the request with a clear explanation.
4. **Given** a merchant enables `DEFERRED_PAYMENT`, **When** an eligible order is created, **Then** the order may choose deferred payment, but deferred payment is rejected if neither `PRE_ORDER` nor `WALK_IN_ORDERING` is enabled.

---

### User Story 3 - Preserve operational continuity for existing merchant and order data (Priority: P2)

An operator can rely on existing merchant, branch, and order records continuing to work after the refactor, with business identity, statuses, and branch-linked order behaviour migrated into the new operational model.

**Why this priority**: The system already contains merchant, branch, and order records. The refactor only succeeds if existing data remains usable under the new language and rules.

**Independent Test**: Migrate existing merchant, branch, and order records, then retrieve them and verify the new business identifiers, statuses, and branch-linked order rules are available without manual re-entry.

**Acceptance Scenarios**:

1. **Given** an existing merchant record still uses onboarding-flavoured identity and status language, **When** the refactor migration is applied, **Then** the merchant is represented as an operational merchant with a merchant code and simple operational status.
2. **Given** existing branch records belong to a merchant, **When** the migration is applied, **Then** each branch receives a stable merchant-scoped branch code and retains independent branch status.
3. **Given** an existing order record belongs to a branch, **When** it is retrieved after the refactor, **Then** it still points to a specific branch and exposes an explicit order type rather than relying on inferred behaviour.

### Edge Cases

- What happens when a merchant exists with no branches and no offerings yet? The merchant remains valid but cannot serve branch-based customer flows until a branch exists and the required offering is enabled.
- What happens when a branch is active but its merchant is inactive? The merchant-level inactive status blocks new customer-facing flows for all branches.
- What happens when a customer requests walk-in ordering without geolocation, with stale geolocation, or with low-accuracy geolocation? The system rejects walk-in ordering.
- What happens when a customer is physically at the branch but intentionally chooses pre-order? The system allows pre-order if the merchant has enabled it.
- What happens when a branch has no contact override? The merchant's public contact info remains the customer-facing default.
- What happens when an operator tries to enable deferred payment without either pre-order or walk-in ordering? The system rejects that offering combination.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST represent the merchant in this context as an operational merchant rather than an onboarding application record.
- **FR-002**: The system MUST assign every merchant a stable system-generated merchant code that is distinct from the editable merchant name.
- **FR-003**: The system MUST allow merchant name changes without changing the merchant code.
- **FR-004**: The system MUST support a simple merchant operational lifecycle with at least `ACTIVE` and `INACTIVE` states.
- **FR-005**: The system MUST allow merchant address, merchant location, and merchant contact info to be absent at merchant creation time.
- **FR-006**: The system MUST support merchant-level public contact info consisting of website, email, and phone number when provided.
- **FR-007**: The system MUST allow a merchant to exist before any branch is created.
- **FR-008**: The system MUST require every branch to belong to exactly one merchant.
- **FR-009**: The system MUST assign every branch a stable system-generated branch code that is unique within its parent merchant.
- **FR-010**: The system MUST allow branch name changes without changing the branch code.
- **FR-011**: The system MUST maintain an operational branch status that is independent from merchant status.
- **FR-012**: The system MUST require every branch to have a structured address.
- **FR-013**: The system MUST require every branch to have geospatial location data sufficient for branch-area validation.
- **FR-014**: The system MUST treat Google Place data and other place-enrichment details as optional for both merchants and branches.
- **FR-015**: The system MUST allow each branch to override merchant website, email, and phone details, but MUST NOT require the override to be present.
- **FR-016**: The system MUST maintain a fixed platform-managed catalogue of merchant offerings.
- **FR-017**: The system MUST allow a merchant to enable zero or more offerings from that catalogue.
- **FR-018**: The system MUST configure offerings at the merchant level only; branch-level offering overrides MUST NOT be supported in this refactor.
- **FR-019**: The system MUST include `PRE_ORDER`, `WALK_IN_ORDERING`, `DEFERRED_PAYMENT`, and `APPOINTMENT_BOOKING` in the offering catalogue.
- **FR-020**: The system MUST treat `APPOINTMENT_BOOKING` as an offering that can be enabled on a merchant, while leaving appointment workflow and persistence out of scope for this refactor.
- **FR-021**: The system MUST reject an attempt to enable `DEFERRED_PAYMENT` unless `PRE_ORDER` or `WALK_IN_ORDERING` is also enabled.
- **FR-022**: The system MUST require every order to belong to a specific branch.
- **FR-023**: The system MUST store an explicit order type on every order to distinguish pre-order from walk-in ordering.
- **FR-024**: The system MUST require the requested order type to be validated against the merchant's enabled offerings before order creation succeeds.
- **FR-025**: The system MUST allow pre-order creation regardless of the customer's proximity to the branch, as long as pre-order is enabled and normal merchant and branch gates permit the flow.
- **FR-026**: The system MUST allow walk-in ordering only when walk-in ordering is enabled and the backend verifies the customer's geolocation against the branch location rules.
- **FR-027**: The system MUST reject walk-in ordering when customer geolocation is missing.
- **FR-028**: The system MUST reject walk-in ordering when customer geolocation is not recent or not sufficiently accurate.
- **FR-029**: The system MUST allow a merchant with deferred payment enabled to create orders that either use deferred payment or do not use deferred payment on a per-order basis.
- **FR-030**: The system MUST block new customer-facing branch flows when the merchant is inactive, even if the branch itself is active.
- **FR-031**: The system MUST block new customer-facing branch flows when the branch is inactive, without retroactively invalidating existing orders.
- **FR-032**: Existing merchant records MUST be migrated from onboarding-flavoured business identity to the operational merchant model defined in this feature.
- **FR-033**: Existing merchant statuses that represent onboarding workflow MUST be replaced by the simpler operational merchant status model.
- **FR-034**: Existing branches MUST receive stable merchant-scoped branch codes during migration.
- **FR-035**: Existing orders MUST continue to reference a specific branch after migration.
- **FR-036**: Existing orders MUST expose an explicit order type after migration instead of relying solely on inferred ordering behaviour.

### Key Entities *(include if feature involves data)*

- **Merchant**: The operational business entity identified by a stable merchant code, editable merchant name, operational merchant status, optional merchant address, optional merchant location, and optional merchant-level public contact info.
- **Branch**: The operational location of a merchant identified by a merchant-scoped branch code, editable branch name, independent branch status, mandatory structured address, mandatory location data, and optional contact override.
- **Merchant Offering**: A platform-managed offering that a merchant may enable, including pre-order, walk-in ordering, deferred payment, and appointment booking.
- **Order**: A branch-owned commercial transaction that carries an explicit order type and may optionally choose deferred payment when permitted by merchant offerings.
- **Place Enrichment**: Optional provider-derived place metadata associated with a merchant or branch without being required for the core validity of either record.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of newly created merchants receive a stable merchant code and operational merchant status without requiring onboarding workflow data.
- **SC-002**: 100% of newly created branches receive a merchant-scoped branch code and are rejected if mandatory address or location data is missing.
- **SC-003**: 100% of customer order requests are accepted only when the relevant merchant offering is enabled and the merchant and branch status gates permit the flow.
- **SC-004**: 100% of walk-in ordering requests without acceptable customer geolocation are rejected before order creation.
- **SC-005**: 100% of migrated merchant and branch records remain retrievable through the operational merchant model after the refactor.
- **SC-006**: Support and operations users can identify a merchant or branch by its stable code alone in at least 95% of sampled operational tasks.

## Assumptions

- This refactor covers the operational merchant context only and intentionally excludes onboarding, compliance review, and application-processing workflow.
- Appointment booking remains part of the merchant offering catalogue, but appointment workflow, slot management, and appointment persistence are outside the scope of this feature.
- Existing product, payment, and order capabilities continue to exist; this feature changes merchant and branch structure, offering governance, and order validation rules rather than redefining product or payment domains.
- Existing orders already belong to a branch in business terms, even where current persistence allows looser linkage.
- Merchant vertical classification remains a merchant-level concern and is not being redesigned by this feature.
