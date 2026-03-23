# Implementation Plan: Merchant Onboarding

**Branch**: `[001-merchant-onboarding]` | **Date**: 2026-03-23 | **Spec**: [spec.md](/Users/mehdi/MyProject/BlitzPay/specs/001-merchant-onboarding/spec.md)
**Input**: Feature specification from `/specs/001-merchant-onboarding/spec.md`

## Summary

Implement merchant onboarding as a new bounded workflow in BlitzPay that covers merchant submission, compliance review, approval decisioning, setup/activation, and post-activation monitoring for EU merchants. The plan starts with the most stable and highest-risk foundations first: domain model, lifecycle/state transitions, compliance decision flow, and audit/privacy controls. Delivery should proceed as vertical slices so that each phase leaves behind testable business value and explicit contracts.

## Technical Context

**Language/Version**: Kotlin 2.3.x on Java 25  
**Primary Dependencies**: Spring Boot 4, Spring WebFlux, Spring Data JPA, Spring Modulith, Springdoc OpenAPI, PostgreSQL driver, Testcontainers  
**Storage**: PostgreSQL  
**Testing**: JUnit 5, Spring Boot Test, Modulith tests, WebFlux tests, JPA tests, Testcontainers  
**Target Platform**: Linux-hosted backend service  
**Project Type**: Modular backend web service  
**Performance Goals**: Merchant submission completes in under 10 minutes for end users; reviewer decision turnaround supports 90% of complete cases within 2 business days  
**Constraints**: EU AML/KYB and GDPR obligations are in scope; contracts and failure modes must be explicit; every behavior change requires automated verification; structured observability is mandatory; every HTTP API must use Spring Boot path-based versioning and belong to an explicit Swagger/OpenAPI group  
**Scale/Scope**: One onboarding workflow spanning merchant intake, review, activation, and monitoring for business merchants in the EU market

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Secrets and Key Material Are Never Exposed**: Pass. Onboarding must keep merchant PII, bank details, and any provider credentials out of source control and out of unredacted logs.
- **II. Payment Requests Must Be Cryptographically Verifiable**: Pass with monitoring. Merchant onboarding itself is not a payment request flow, but any setup handoff to payment providers must preserve signed-provider requirements and explicit request boundaries.
- **III. Every Behavior Change Requires Automated Verification**: Pass only if each slice ships with automated tests covering lifecycle transitions, compliance rules, and regressions.
- **IV. Contracts and Failure Modes Must Be Explicit**: Pass only if onboarding APIs, review actions, monitoring actions, and provider touchpoints have explicit schemas and deterministic error behavior.
- **V. Production Behavior Must Be Observable**: Pass only if onboarding events, reviewer decisions, status transitions, and provider failures emit structured logs, audit events, and metrics.

## Project Structure

### Documentation (this feature)

```text
specs/001-merchant-onboarding/
├── plan.md
├── spec.md
├── merchant_onboarding_spec.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md
```

### Source Code (repository root)

```text
src/main/kotlin/com/elegant/software/blitzpay/
├── config/
├── invoice/
├── payments/
│   ├── qrpay/
│   ├── support/
│   └── truelayer/
└── merchant/
    ├── api/
    ├── application/
    ├── domain/
    ├── repository/
    ├── support/
    ├── compliance/
    ├── activation/
    └── monitoring/

src/test/kotlin/com/elegant/software/blitzpay/
├── merchant/
│   ├── contract/
│   ├── integration/
│   └── unit/
└── config/
```

**Structure Decision**: Use the existing single Spring Boot service and add merchant onboarding as a new Modulith-aligned module rooted under `src/main/kotlin/com/elegant/software/blitzpay/merchant`. Use `repository/` for Spring Data JPA repositories and `support/` for technical support components such as logging-backed audit or observability helpers. Keep module APIs explicit and avoid direct coupling into existing payment internals except through published interfaces.

## Phase 0 Research

- Confirm the exact onboarding handoff points to existing payment/provider integrations, especially TrueLayer-related account setup.
- Confirm the legal retention window and approved regional storage boundary expected for EU onboarding records.
- Decide whether sanctions/PEP screening is implemented internally, delegated to a provider, or stubbed behind an adapter for this feature slice.
- Document failure handling for duplicate applications, incomplete compliance evidence, provider timeouts, and reviewer conflict cases.

## Phase 1 Design

### Domain First

- Define the core entities: `MerchantApplication`, `BusinessProfile`, `Person`, `SupportingMaterial`, `RiskAssessment`, `ReviewDecision`, `MonitoringRecord`.
- Define uniqueness rules for merchant applications, business registration identifiers, beneficial-owner identities, and monitoring records.
- Define canonical lifecycle states and allowed transitions:
  `draft -> submitted -> verification -> screening -> risk review -> decision pending -> setup -> active -> monitoring`

### Contracts

- Define merchant-facing APIs for starting, saving, submitting, and retrieving onboarding applications.
- Define internal reviewer APIs for requesting changes, recording review outcomes, approving/rejecting, and managing monitoring events.
- Define provider-facing adapters and contracts for setup/activation handoffs without leaking provider-specific concerns into the merchant domain model.
- Define the merchant module's explicit Swagger/OpenAPI group and keep all exposed endpoints on Spring Boot path-based versioned routes.

### Quality Controls

- Define audit events for every status transition and reviewer decision.
- Define PII handling rules for storage, response payloads, and log redaction.
- Define observability outputs: structured logs, counters for lifecycle transitions, and failure metrics for compliance/provider steps.

## Delivery Slices

### Slice 1: Merchant Submission

- Create merchant onboarding module skeleton and internal package boundaries.
- Implement draft creation, save/resume, validation, review-before-submit, and submission reference generation.
- Expose merchant submission APIs under path-based versioned routes and register them in a dedicated Swagger/OpenAPI group.
- Add contract and integration tests for happy path, invalid input, and duplicate-application prevention.

### Slice 2: Compliance Review

- Implement verification, screening, risk review, and action-required decision flow.
- Add reviewer decision recording, change requests, resubmission handling, and audit history.
- Expose reviewer-facing APIs under path-based versioned routes and include them in the merchant module's Swagger/OpenAPI group.
- Add tests for lifecycle transitions, correction loops, rejection flow, and compliance-required data completeness.

### Slice 3: Activation and Setup

- Implement approval-to-setup-to-active flow with explicit handoff boundary to provider/account-setup logic.
- Record setup outcomes, failures, retries, and final active status.
- Add tests for approval success, setup failure, and idempotent activation handling.

### Slice 4: Monitoring

- Implement monitoring record creation for active merchants.
- Support follow-up review actions, monitoring-trigger events, and ongoing oversight state updates.
- Add tests for monitoring entry, monitoring-triggered review, and history preservation.

### Slice 5: Audit, Privacy, and Operational Controls

- Enforce access restrictions, retention policy hooks, regional storage assumptions, and GDPR-oriented data handling.
- Add structured logging, metrics, and health visibility for the onboarding module.
- Add tests for unauthorized access, audit trail completeness, and retention/control behavior that can be validated at application level.

## Test Strategy

- **Unit tests**: state transition rules, validation logic, risk/compliance decision helpers, duplicate detection, retention/access policy helpers.
- **Integration tests**: persistence of onboarding lifecycle, reviewer workflows, monitoring creation, and transactional consistency.
- **Contract tests**: merchant-facing and reviewer-facing HTTP APIs, request/response schemas, validation errors, and provider adapter boundaries.
- **Modularity tests**: verify the merchant module exposes only intended APIs and does not reach into internal payment packages directly.

## Risks and Mitigations

- **Compliance scope creep**: keep the spec boundary intact and push provider-specific implementation detail into adapters.
- **Lifecycle inconsistency**: centralize transition rules and test invalid transitions explicitly.
- **PII leakage**: enforce redaction and access-control checks from the first slice rather than as a cleanup task.
- **Provider coupling**: isolate activation/setup behind internal interfaces so onboarding remains stable if provider details change.

## Immediate Next Planning Work

1. Produce `research.md` for compliance retention, storage boundary, screening-provider, and activation-handoff decisions.
2. Produce `data-model.md` with entities, relationships, invariants, and lifecycle transitions.
3. Produce API contracts under `contracts/` for merchant submission and reviewer decision flows.
4. Generate `tasks.md` from the delivery slices once the above artifacts are in place.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None at plan stage | N/A | N/A |
