# Tasks: Merchant Registration (Skip Onboarding)

**Input**: Design documents from `/specs/001-merchant-onboarding/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Scope decision**: Full onboarding workflow deferred. This task list covers only the direct merchant registration path: `POST /v1/merchants` creates and activates a merchant; `GET /v1/merchants/{merchantId}` retrieves it.

**Tests**: Test tasks are included for contract tests (WebTestClient, `contract-test` profile) and unit tests for the service layer, consistent with the project's testing conventions.

**Organization**: Tasks are grouped by phase. The foundational domain changes are in Phase 2; the registration feature is Phase 3 (single user story equivalent). Deferred onboarding stories are listed at the end as a reference for future sprints.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: User story label (`US1` = Register Merchant directly)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm design artifacts are in place before implementation begins. No code changes in this phase.

- [ ] T001 Verify `specs/001-merchant-onboarding/research.md`, `data-model.md`, and `contracts/merchant-registration.md` are present and consistent with plan.md

---

## Phase 2: Foundational Domain Changes (Blocking Prerequisites)

**Purpose**: Extend the existing domain model to support the `DRAFT → ACTIVE` direct registration path. These changes are required before the service and controller can be built.

**⚠️ CRITICAL**: Phase 3 cannot start until this phase is complete.

- [ ] T002 Add `ACTIVE` to the allowed-next set for `DRAFT` in the transition map in `src/main/kotlin/com/elegant/software/blitzpay/merchant/domain/MerchantOnboardingLifecycle.kt`
- [ ] T003 Add `registerDirect(activatedAt: Instant)` method to `MerchantApplication` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/domain/MerchantApplication.kt` — calls `MerchantOnboardingLifecycle.requireTransition(status, ACTIVE)`, sets `status = ACTIVE`, `submittedAt = activatedAt`, calls `touch(activatedAt)`
- [ ] T004 [P] Add unit test for the `DRAFT → ACTIVE` lifecycle transition and `registerDirect()` in `src/test/kotlin/com/elegant/software/blitzpay/merchant/domain/MerchantApplicationTest.kt`

**Checkpoint**: `./gradlew test` passes with the new lifecycle transition and domain method tests.

---

## Phase 3: User Story 1 — Register Merchant (Priority: P1) 🎯 MVP

**Goal**: A caller can `POST /v1/merchants` with business profile + primary contact and receive a `201 Created` with the merchant in `ACTIVE` status. A caller can `GET /v1/merchants/{merchantId}` to retrieve it. The endpoint enforces no-duplicate-registration by `registrationNumber`.

**Independent Test**: `POST /v1/merchants` with valid payload returns 201 and `"status": "ACTIVE"`. `POST /v1/merchants` with the same `registrationNumber` returns 409. `GET /v1/merchants/{id}` returns the previously registered merchant. `GET /v1/merchants/{unknownId}` returns 404.

### Contract Tests for User Story 1

> **Write these FIRST — they must FAIL before implementation tasks T007–T009 are started.**

- [ ] T005 [P] [US1] Write contract test for `POST /v1/merchants` (happy path: 201 + ACTIVE status, body fields) in `src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/MerchantControllerTest.kt`
- [ ] T006 [P] [US1] Write contract test for `POST /v1/merchants` (conflict: duplicate registrationNumber → 409) and `GET /v1/merchants/{merchantId}` (200 and 404) in `src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/MerchantControllerTest.kt`

### Implementation for User Story 1

- [ ] T007 [US1] Add `RegisterMerchantRequest` data class to `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantOnboardingModels.kt` — fields: `businessProfile: MerchantBusinessProfileRequest`, `primaryContact: MerchantPrimaryContactRequest`
- [ ] T008 [US1] Create `MerchantRegistrationService` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/MerchantRegistrationService.kt` with:
  - `register(request: RegisterMerchantRequest): MerchantApplication` — checks duplicate by `registrationNumber` via `MerchantApplicationRepository.existsByBusinessProfileRegistrationNumberAndStatusIn`, generates `applicationReference` as `"BLTZ-" + UUID.randomUUID().toString().take(8).uppercase()`, creates `MerchantApplication`, calls `registerDirect()`, saves, audits
  - `findById(merchantId: UUID): MerchantApplication` — `findById` or throw `NoSuchElementException`
- [ ] T009 [US1] Create `MerchantController` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantController.kt` — `@RestController @RequestMapping("/v1/merchants")`:
  - `POST /v1/merchants` → `Mono.fromCallable { merchantRegistrationService.register(request) }.subscribeOn(Schedulers.boundedElastic()).map { it.toResponse() }.map { ResponseEntity.status(201).body(it) }`
  - `GET /v1/merchants/{merchantId}` → `Mono.fromCallable { merchantRegistrationService.findById(merchantId) }.subscribeOn(Schedulers.boundedElastic()).map { it.toResponse() }.map { ResponseEntity.ok(it) }`
  - Map `NoSuchElementException` → 404, `IllegalArgumentException` with "already exists" → 409, other `IllegalArgumentException` → 400
  - Include private `MerchantApplication.toResponse()` extension mapping to `MerchantApplicationResponse`
- [ ] T010 [P] [US1] Write unit tests for `MerchantRegistrationService` in `src/test/kotlin/com/elegant/software/blitzpay/merchant/application/MerchantRegistrationServiceTest.kt` — cover: successful registration, duplicate rejection, not-found case

**Checkpoint**: `./gradlew check` passes. `POST /v1/merchants` returns 201 with `status: ACTIVE`. Contract tests green.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Module integrity verification and OpenAPI documentation registration.

- [ ] T011 [P] Add the `/v1/merchants/**` path group to `MerchantOpenApiConfig` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/config/OpenApiConfig.kt` if not already scanned
- [ ] T012 [P] Run and pass Spring Modulith boundary verification — confirm `MerchantRegistrationService` and `MerchantController` do not leak internal types across module boundaries; update or add `ApplicationModulesTest` in `src/test/kotlin/com/elegant/software/blitzpay/merchant/` if needed
- [ ] T013 Run `./gradlew clean build` to confirm full build including all tests passes

---

## Deferred User Stories (Future Sprints)

These user stories are preserved from the original spec but are out of scope for this branch.

| Story | Title | Blocked By |
|-------|-------|------------|
| US2 | Track Onboarding Status (P2) | Reviewer tooling, merchant portal |
| US3 | Review and Approve Applications (P3) | KYB/AML screening integration, reviewer UI |

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — starts immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS Phase 3
- **Phase 3 (US1)**: Depends on Phase 2 completion
  - T005, T006 (contract tests): Write first, fail first
  - T007 (model change): Parallel with T005/T006
  - T008 (service): Depends on T007 (uses `RegisterMerchantRequest`), T003 (uses `registerDirect()`)
  - T009 (controller): Depends on T008
  - T010 (unit test): Parallel with T009 (different file)
- **Phase 4 (Polish)**: Depends on Phase 3 completion

### Within Phase 3

```
T005, T006 (write contract tests — FAIL)
T007 (add RegisterMerchantRequest)
    ↓
T008 (MerchantRegistrationService)          T010 (unit tests — parallel)
    ↓
T009 (MerchantController)
```

### Parallel Opportunities

- T004 and T005/T006 can all start once Phase 2 domain changes are written (T002, T003)
- T010 (unit tests) can be written in parallel with T009 (controller)
- T011 and T012 can run in parallel within Phase 4

---

## Parallel Example: Phase 3

```
# Once T002 + T003 are done, launch in parallel:
Task T004: "Unit test for registerDirect() in MerchantApplicationTest.kt"
Task T005: "Contract test POST /v1/merchants happy path in MerchantControllerTest.kt"
Task T006: "Contract test POST /v1/merchants conflict + GET in MerchantControllerTest.kt"
Task T007: "Add RegisterMerchantRequest to MerchantOnboardingModels.kt"

# Once T007 is done:
Task T008: "Create MerchantRegistrationService.kt"

# Once T008 is done, launch in parallel:
Task T009: "Create MerchantController.kt"
Task T010: "Unit tests for MerchantRegistrationService in MerchantRegistrationServiceTest.kt"
```

---

## Implementation Strategy

### MVP (This Branch)

1. Complete Phase 2: domain lifecycle + `registerDirect()` method
2. Write contract tests (T005, T006) — verify they FAIL
3. Add model, service, controller (T007–T009)
4. Verify contract tests pass: `./gradlew contractTest`
5. Run full build: `./gradlew clean build`

### Future Increments

- Sprint N+1: Merchant status tracking endpoint (`GET /v1/merchants/{id}/status`)
- Sprint N+2: Internal reviewer endpoints (transition, decision, request-changes)
- Sprint N+3: KYB/AML integration hooks, monitoring record management

---

## Notes

- `[P]` tasks touch different files — they can be launched as parallel agents
- All blocking JPA calls in the controller must be wrapped with `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`
- Contract tests run under the `contract-test` Spring profile — no real DB, DataSource auto-config excluded, TrueLayer mocked
- `MerchantRegistrationService` is separate from `MerchantOnboardingService` — do not add `register()` to the existing service
- The `DRAFT → ACTIVE` transition is available via the generic `transitionTo()` path after T002, but `registerDirect()` is the intended entry point for this use case
