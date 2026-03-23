# Tasks: Merchant Onboarding

**Input**: Design documents from `/specs/001-merchant-onboarding/`
**Prerequisites**: plan.md, spec.md

**Tests**: Automated tests are required by the repository constitution for any behavior change.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`US1`, `US2`, `US3`)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the merchant onboarding module skeleton and feature documentation outputs.

- [ ] T001 Create feature research notes in `specs/001-merchant-onboarding/research.md`
- [ ] T002 Create feature data model in `specs/001-merchant-onboarding/data-model.md`
- [ ] T003 Create contract directory and initial API contract files in `specs/001-merchant-onboarding/contracts/`
- [ ] T004 Create merchant module package structure under `src/main/kotlin/com/elegant/software/blitzpay/merchant/`
- [ ] T005 [P] Create merchant test package structure under `src/test/kotlin/com/elegant/software/blitzpay/merchant/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build the core domain, persistence, lifecycle, security, and observability pieces required by all user stories.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T006 Create merchant lifecycle/state model in `src/main/kotlin/com/elegant/software/blitzpay/merchant/domain/`
- [ ] T007 [P] Create core domain entities for `MerchantApplication`, `BusinessProfile`, `Person`, `SupportingMaterial`, `RiskAssessment`, `ReviewDecision`, and `MonitoringRecord` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/domain/`
- [ ] T008 Create repositories and persistence mappings in `src/main/kotlin/com/elegant/software/blitzpay/merchant/repository/`
- [ ] T009 Create lifecycle transition and validation services in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/`
- [ ] T010 [P] Create module API boundaries and package exposure rules for the merchant module in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/`
- [ ] T011 [P] Add access-control, PII redaction, and audit event support in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/` and `src/main/kotlin/com/elegant/software/blitzpay/merchant/support/`
- [ ] T012 [P] Add observability support for onboarding events, state transitions, and failures in `src/main/kotlin/com/elegant/software/blitzpay/merchant/support/`
- [ ] T013 [P] Create foundational unit tests for lifecycle rules and validation helpers in `src/test/kotlin/com/elegant/software/blitzpay/merchant/unit/`
- [ ] T014 [P] Create persistence/integration tests for merchant core entities and repositories in `src/test/kotlin/com/elegant/software/blitzpay/merchant/integration/`
- [ ] T015 Create modulith boundary test for the merchant module in `src/test/kotlin/com/elegant/software/blitzpay/merchant/`

**Checkpoint**: Foundation ready, user story work can now begin in priority order or in parallel if staffed.

---

## Phase 3: User Story 1 - Submit Merchant Application (Priority: P1) 🎯 MVP

**Goal**: Allow a prospective merchant to start onboarding, save progress, validate input, review details, and submit a compliant application.

**Independent Test**: A merchant can create a draft, complete required business/contact/ownership details, review the application, submit successfully, and receive a unique application reference.

### Tests for User Story 1

- [ ] T016 [P] [US1] Add contract tests for merchant onboarding create/save/submit endpoints in `src/test/kotlin/com/elegant/software/blitzpay/merchant/contract/`
- [ ] T017 [P] [US1] Add integration test for draft save/resume and submission journey in `src/test/kotlin/com/elegant/software/blitzpay/merchant/integration/`
- [ ] T018 [P] [US1] Add integration test for duplicate active-application prevention in `src/test/kotlin/com/elegant/software/blitzpay/merchant/integration/`

### Implementation for User Story 1

- [ ] T019 [P] [US1] Create merchant application request/response models in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/`
- [ ] T020 [P] [US1] Implement merchant submission validation rules in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/`
- [ ] T021 [US1] Implement draft creation, save, resume, and review service in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/`
- [ ] T022 [US1] Implement merchant onboarding HTTP endpoints in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/`
- [ ] T023 [US1] Implement submission reference generation and submission audit events in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/`
- [ ] T024 [US1] Add a dedicated Swagger/OpenAPI group and Spring Boot path-based versioning for merchant submission APIs in `src/main/kotlin/com/elegant/software/blitzpay/merchant/config/` and `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/`

**Checkpoint**: User Story 1 is independently functional and testable.

---

## Phase 4: User Story 2 - Track Onboarding Status (Priority: P2)

**Goal**: Allow merchants to view current onboarding status, see action-required details, and understand next steps.

**Independent Test**: After submission and status changes, a merchant can retrieve the current lifecycle state, outstanding actions, and decision outcome without internal assistance.

### Tests for User Story 2

- [ ] T025 [P] [US2] Add contract tests for onboarding status retrieval endpoints in `src/test/kotlin/com/elegant/software/blitzpay/merchant/contract/`
- [ ] T026 [P] [US2] Add integration test for action-required status and merchant resubmission visibility in `src/test/kotlin/com/elegant/software/blitzpay/merchant/integration/`

### Implementation for User Story 2

- [ ] T027 [P] [US2] Create status view models and action-required response models in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/`
- [ ] T028 [US2] Implement onboarding status query service in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/`
- [ ] T029 [US2] Implement merchant status and next-step endpoints with Spring Boot path-based versioning in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/`
- [ ] T030 [US2] Implement merchant resubmission handling for requested corrections in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/`
- [ ] T031 [US2] Add merchant-facing status change notifications in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/`

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 - Review, Approve, Activate, and Monitor Merchants (Priority: P3)

**Goal**: Allow internal reviewers to run compliance review, make decisions, activate approved merchants, and place them into ongoing monitoring.

**Independent Test**: An internal reviewer can review a submitted application, request corrections or approve/reject it, activate approved merchants, and record monitoring events on active merchants.

### Tests for User Story 3

- [ ] T032 [P] [US3] Add contract tests for reviewer decision, activation, and monitoring endpoints in `src/test/kotlin/com/elegant/software/blitzpay/merchant/contract/`
- [ ] T033 [P] [US3] Add integration test for verification, screening, risk review, and approval flow in `src/test/kotlin/com/elegant/software/blitzpay/merchant/integration/`
- [ ] T034 [P] [US3] Add integration test for approval-to-activation handoff and monitoring record creation in `src/test/kotlin/com/elegant/software/blitzpay/merchant/integration/`
- [ ] T035 [P] [US3] Add integration test for monitoring-triggered follow-up actions in `src/test/kotlin/com/elegant/software/blitzpay/merchant/integration/`

### Implementation for User Story 3

- [ ] T036 [P] [US3] Create reviewer decision, activation, and monitoring request/response models in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/`
- [ ] T037 [US3] Implement compliance review workflow for verification, screening, risk review, and decisioning in `src/main/kotlin/com/elegant/software/blitzpay/merchant/compliance/`
- [ ] T038 [US3] Implement reviewer decision and action-required services in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/`
- [ ] T039 [US3] Implement activation/setup handoff service in `src/main/kotlin/com/elegant/software/blitzpay/merchant/activation/`
- [ ] T040 [US3] Implement monitoring record creation and follow-up workflow in `src/main/kotlin/com/elegant/software/blitzpay/merchant/monitoring/`
- [ ] T041 [US3] Implement internal reviewer HTTP endpoints with Spring Boot path-based versioning and Swagger group coverage in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/`

**Checkpoint**: All user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Finalize cross-story controls and delivery readiness.

- [ ] T042 [P] Add GDPR retention and regional-storage policy documentation in `specs/001-merchant-onboarding/research.md`
- [ ] T043 [P] Update `specs/001-merchant-onboarding/quickstart.md` with merchant onboarding validation steps
- [ ] T044 Add structured logging and metrics verification across merchant flows in `src/test/kotlin/com/elegant/software/blitzpay/merchant/integration/`
- [ ] T045 Add security and unauthorized-access regression tests in `src/test/kotlin/com/elegant/software/blitzpay/merchant/integration/`
- [ ] T046 Run end-to-end feature verification and update `specs/001-merchant-onboarding/tasks.md` with completion state

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: No dependencies
- **Phase 2: Foundational**: Depends on Phase 1 and blocks all user stories
- **Phase 3: US1**: Depends on Phase 2
- **Phase 4: US2**: Depends on Phase 2 and can reuse US1 outputs
- **Phase 5: US3**: Depends on Phase 2 and integrates with US1/US2 lifecycle behavior
- **Phase 6: Polish**: Depends on completion of the desired user stories

### User Story Dependencies

- **US1**: First MVP slice, no dependency on later stories
- **US2**: Depends on foundational lifecycle and submitted applications from US1
- **US3**: Depends on foundational lifecycle and submitted applications from US1; complements US2 status visibility

### Within Each User Story

- Tests should be written before implementation and fail on missing behavior
- API models before services
- Services before controllers/endpoints
- All controllers must use path-based API versioning and belong to an explicit Swagger/OpenAPI group
- Core workflow before notifications/observability refinements

### Parallel Opportunities

- `T005` can run alongside `T004`
- `T007`, `T010`, `T011`, `T012`, `T013`, and `T014` can run in parallel after `T006`
- `T016`, `T017`, and `T018` can run in parallel
- `T019` and `T020` can run in parallel before `T021`
- `T025` and `T026` can run in parallel
- `T027` can run in parallel with `T028`
- `T032` to `T035` can run in parallel
- `T036` can run in parallel with `T037`

## Implementation Strategy

### MVP First

1. Complete Phase 1
2. Complete Phase 2
3. Complete Phase 3
4. Validate merchant submission independently before moving on

### Incremental Delivery

1. Deliver US1 as the merchant intake MVP
2. Add US2 for self-service status visibility
3. Add US3 for internal review, activation, and monitoring
4. Finish with privacy, observability, and operational hardening

### Team Strategy

1. One developer handles foundational domain and persistence work
2. One developer can take merchant-facing APIs and status endpoints after Phase 2
3. One developer can take reviewer/compliance/monitoring flows after Phase 2

## Notes

- `[P]` tasks are intended to avoid file conflicts where practical
- Keep Spring Modulith boundaries explicit for the new merchant module
- Avoid direct coupling from merchant onboarding into payment internals; use module APIs or adapters
- Do not skip automated tests for behavior changes
