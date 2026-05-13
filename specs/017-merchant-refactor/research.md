# Research: Merchant Refactor

## Decision: Rename the operational merchant aggregate and remove onboarding language from the primary merchant model

- **Decision**: Treat the current operational parent aggregate as `Merchant`, retire `MerchantApplication` language from the operational model, and rename persistence, API, and service surfaces to merchant-oriented names.
- **Rationale**: `CONTEXT.md` explicitly scopes this module to operational merchant management, not onboarding workflow. Keeping `MerchantApplication` as the core operational aggregate would preserve the domain confusion the refactor is meant to remove.
- **Alternatives considered**:
  - Keep `MerchantApplication` and reinterpret it as merchant: rejected because it preserves misleading language in code, schema, APIs, and cross-module contracts.
  - Split onboarding into a new aggregate in the same refactor: rejected because onboarding is out of scope for this feature and would expand the change beyond the operational merchant boundary.

## Decision: Migrate the parent merchant table and foreign keys in place to operational names

- **Decision**: Rename `merchant_applications` to `merchant_merchants`, rename `merchant_application_id` foreign keys to `merchant_id`, and rename operational API fields and DTO properties accordingly.
- **Rationale**: The repo constitution requires Liquibase-owned schema evolution and stable module ownership. In-place renaming preserves existing records while aligning the schema with the new domain language.
- **Alternatives considered**:
  - Create a new merchant table and copy data: rejected because it increases migration risk, duplicates operational data, and complicates cross-module references.
  - Leave old table and column names untouched: rejected because it would keep onboarding terminology embedded in the operational path.

## Decision: Replace onboarding lifecycle states with a simpler operational merchant status

- **Decision**: Use a simple operational merchant status model with `ACTIVE` and `INACTIVE`. Migrate existing statuses so `ACTIVE` and `MONITORING` become `ACTIVE`, while all other onboarding-oriented statuses become `INACTIVE`.
- **Rationale**: The new context excludes onboarding and compliance workflow. The old status graph contains workflow states that are no longer meaningful for the operational merchant.
- **Alternatives considered**:
  - Preserve the full onboarding lifecycle: rejected because it violates the agreed domain scope.
  - Introduce additional operational states immediately such as `SUSPENDED`: rejected because the spec only requires a simpler lifecycle and does not yet define extra operational semantics.

## Decision: Keep branch status independent but subordinate to merchant status for traffic gating

- **Decision**: Maintain a separate branch status while treating merchant inactivity as a global gate that blocks new customer-facing branch flows.
- **Rationale**: Branches need local operational control, but the merchant remains the top-level owner. This matches the domain decisions captured in `CONTEXT.md`.
- **Alternatives considered**:
  - Derive branch status solely from merchant status: rejected because it removes legitimate per-branch operational control.
  - Let active branches continue serving traffic under an inactive merchant: rejected because it creates contradictory top-level state.

## Decision: Model merchant offerings as a platform-managed catalogue with merchant assignment records

- **Decision**: Add a `merchant_offerings` catalogue and a merchant-to-offering assignment table instead of hard-coded merchant flags.
- **Rationale**: This matches the repo’s existing DB-driven vertical strategy, avoids schema churn when offerings evolve, and preserves the fixed controlled vocabulary agreed during domain definition.
- **Alternatives considered**:
  - Boolean flags on the merchant row: rejected because each new offering would require schema, entity, API, and migration changes.
  - Free-form merchant-defined offering strings: rejected because offerings are intended to be governed and used in runtime validation.

## Decision: Keep offerings merchant-wide and validate them at runtime in order flows

- **Decision**: Offerings are configured only at merchant scope, inherited by all branches, and enforced when orders are created.
- **Rationale**: The user explicitly chose merchant-wide offerings with no branch overrides in this context. Runtime enforcement is necessary so offerings remain business rules rather than descriptive metadata.
- **Alternatives considered**:
  - Branch-level overrides: rejected because the current domain decision explicitly rules them out.
  - Metadata-only offerings: rejected because order and appointment flows must be gated by merchant configuration.

## Decision: Require explicit order type and branch selection in order creation

- **Decision**: Every new order request should identify both the target branch and the requested order type (`PRE_ORDER` or `WALK_IN_ORDERING`), with backend validation against merchant offerings and geolocation rules.
- **Rationale**: The new domain requires every order to belong to a branch and every order to carry an explicit order type. Explicit request intent is necessary because a customer may still choose pre-order while physically at the branch.
- **Alternatives considered**:
  - Infer order type from geolocation: rejected because it contradicts the resolved rule that customers at the branch may still intentionally choose pre-order.
  - Keep branch implicit for shopper orders: rejected because the new domain treats branch linkage as mandatory and first-class.

## Decision: Backfill explicit order type from existing order context

- **Decision**: Backfill existing merchant-created orders as `WALK_IN_ORDERING` and existing shopper-created orders as `PRE_ORDER`.
- **Rationale**: The current order model records creator type but not order type. Merchant-created orders are the closest existing analogue to in-branch POS flows, while shopper-created orders represent remote customer initiation.
- **Alternatives considered**:
  - Leave old orders without order type: rejected because the refactored domain requires explicit order type on every order.
  - Mark historical order type as unknown: rejected because it would force special-case handling across reads and validations.

## Decision: Validate walk-in ordering using backend geofence rules and quality-checked customer geolocation

- **Decision**: Walk-in ordering is allowed only when the backend receives customer geolocation that is present, recent, sufficiently accurate, and inside the branch decision boundary.
- **Rationale**: Branch location is mandatory in the new model specifically to support operational locality rules. Client-only checks would not satisfy the domain rule.
- **Alternatives considered**:
  - Client-enforced walk-in gating: rejected because it weakens a core business rule.
  - Allow walk-in without location fallback: rejected because the user explicitly chose rejection when geolocation is absent.
