# Feature Specification: Merchant Vertical (Business Type)

**Feature Branch**: `016-merchant-vertical`
**Created**: 2026-05-13
**Status**: Draft

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Platform operator seeds and manages verticals (Priority: P1)

The platform operator needs a catalogue of industry verticals available to merchants. Verticals are managed through database records so new verticals can be added without a code deployment.

**Why this priority**: Nothing else works without verticals existing. This is the foundation all other stories depend on.

**Independent Test**: Seed the `merchant_verticals` table and verify the 9 initial verticals are readable via the API.

**Acceptance Scenarios**:

1. **Given** the system is initialised, **When** the platform starts, **Then** the 9 seeded verticals (`RESTAURANT`, `CAFE`, `BAR`, `BARBER_SHOP`, `BEAUTY_SALON`, `RETAIL`, `GYM`, `PHARMACY`, `ICE_CREAM_SHOP`) are available.
2. **Given** a vertical exists with `active = false`, **When** the verticals list is fetched, **Then** that vertical does not appear in the list returned to merchants.
3. **Given** a new vertical is added via a database changeset, **When** the system reads the list, **Then** the new vertical appears without any code change.

---

### User Story 2 — Merchant selects a vertical during onboarding (Priority: P1)

When a merchant registers or creates an application, they choose their industry vertical from the platform-managed list. The vertical is stored against the merchant and returned in subsequent reads.

**Why this priority**: This replaces the existing free-form `businessType` string. Until this is in place the domain model is inconsistent.

**Independent Test**: Register a merchant with vertical `RESTAURANT`, then fetch the merchant — the response includes `businessVertical: "RESTAURANT"` and `displayName: "Restaurant"`.

**Acceptance Scenarios**:

1. **Given** a valid vertical code, **When** a merchant application is created, **Then** the vertical is stored and returned in the merchant response.
2. **Given** an invalid or unknown vertical code, **When** a merchant application is submitted, **Then** the request is rejected with a descriptive validation error.
3. **Given** an existing merchant with `businessType = "LLC"` in the database, **When** the migration runs, **Then** `business_type` is set to `RETAIL` as the default fallback (migration note: legacy data, not a domain assumption).

---

### User Story 3 — Consumer-facing API exposes merchant vertical (Priority: P2)

Clients (mobile apps, third-party integrations) can retrieve the merchant's vertical and display name to personalise the user experience.

**Why this priority**: The vertical has no consumer value until it is exposed in the API responses.

**Independent Test**: Fetch merchant details — response includes `businessVertical` with both `code` and `displayName`.

**Acceptance Scenarios**:

1. **Given** a merchant with vertical `BARBER_SHOP`, **When** the merchant details endpoint is called, **Then** the response includes `{ "code": "BARBER_SHOP", "displayName": "Barber Shop" }`.
2. **Given** a merchant, **When** the merchant list endpoint is called, **Then** each merchant record includes the vertical code and display name.

---

### Edge Cases

- What happens when `business_type` in the DB references a vertical code that no longer exists in `merchant_verticals`? Referential integrity via FK constraint prevents orphaned references.
- What happens when a client submits an empty or null vertical code? Request is rejected with a validation error — vertical is mandatory.
- What happens when `merchant_verticals` table is empty? No verticals are returned in the list; merchant registration is blocked until at least one active vertical exists.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST maintain a `merchant_verticals` table containing `code`, `display_name`, and `active` fields.
- **FR-002**: The system MUST seed the following 9 verticals on initialisation: `RESTAURANT`, `CAFE`, `BAR`, `BARBER_SHOP`, `BEAUTY_SALON`, `RETAIL`, `GYM`, `PHARMACY`, `ICE_CREAM_SHOP`.
- **FR-003**: The `business_type` column on `merchant_applications` MUST reference `merchant_verticals.code` via a foreign key constraint.
- **FR-004**: Merchant registration and application creation MUST require a valid, active vertical code.
- **FR-005**: The system MUST reject requests containing an unrecognised or inactive vertical code with a descriptive validation error.
- **FR-006**: The merchant details API response MUST include the vertical `code` and `display_name`.
- **FR-007**: The platform MUST expose an endpoint to list all active verticals so clients can populate onboarding forms.
- **FR-008**: The vertical MUST be stored at the merchant level only — branches inherit it from their parent merchant and do not hold their own vertical.
- **FR-009**: Existing `business_type` values (`LLC`, `LIMITED_COMPANY`, `RETAIL`) MUST be migrated; legacy legal-entity values are mapped to `RETAIL` as a neutral default.

### Key Entities

- **MerchantVertical**: An industry classification for a merchant. Fields: `code` (unique identifier, e.g. `RESTAURANT`), `display_name` (human-readable label, e.g. `"Restaurant"`), `active` (whether available for selection).
- **MerchantApplication**: Gains a validated reference to `MerchantVertical` via the existing `business_type` column, now constrained to the `merchant_verticals` catalogue.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A merchant can complete registration with a valid vertical in the same number of steps as before — no additional friction introduced.
- **SC-002**: All 9 seeded verticals are available via the verticals list endpoint immediately after deployment with no manual intervention.
- **SC-003**: 100% of merchant records have a valid, non-null vertical code after the migration runs.
- **SC-004**: Adding a new vertical requires only a database changeset — zero code changes and zero redeployments.
- **SC-005**: Attempting to register with an invalid vertical code results in a clear rejection message in under 1 second.

## Assumptions

- Capability routing (appointment booking for barber shops, table reservation for restaurants) is **out of scope** for this feature.
- Legal entity type (LLC, Limited Company, Sole Trader) is permanently dropped — it was confirmed unused in any business logic.
- The vertical lives at the `MerchantApplication` (merchant) level only. `MerchantBranch` does not have its own vertical.
- Legacy `business_type` values that are legal-entity strings (`LLC`, `LIMITED_COMPANY`) are migrated to `RETAIL` as a neutral fallback — this is a data-cleanup decision, not a domain assumption about those merchants.
- Kotlin enum representation of verticals may be introduced in the future once the set stabilises; for now the source of truth is the `merchant_verticals` table.
